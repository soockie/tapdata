package io.tapdata.entity.conversion.impl;

import io.tapdata.entity.annotations.Implementation;
import io.tapdata.entity.codec.filter.TapCodecsFilterManager;
import io.tapdata.entity.conversion.TargetTypesGenerator;
import io.tapdata.entity.conversion.UnsupportedTypeFallbackHandler;
import io.tapdata.entity.error.CoreException;
import io.tapdata.entity.error.TapAPIErrorCodes;
import io.tapdata.entity.logger.TapLogger;
import io.tapdata.entity.mapping.DefaultExpressionMatchingMap;
import io.tapdata.entity.mapping.type.TapMapping;
import io.tapdata.entity.result.ResultItem;
import io.tapdata.entity.result.TapResult;
import io.tapdata.entity.schema.TapField;
import io.tapdata.entity.schema.type.TapString;
import io.tapdata.entity.utils.InstanceFactory;

import java.math.BigDecimal;
import java.util.*;


@Implementation(value = TargetTypesGenerator.class, buildNumber = 0)
public class TargetTypesGeneratorImpl implements TargetTypesGenerator {
    private static final String TAG = TargetTypesGeneratorImpl.class.getSimpleName();

    public TapResult<LinkedHashMap<String, TapField>> convert(LinkedHashMap<String, TapField> sourceFields, DefaultExpressionMatchingMap targetMatchingMap, TapCodecsFilterManager targetCodecFilterManager) {
        if(sourceFields == null || targetMatchingMap == null)
            return null;
        TapResult<LinkedHashMap<String, TapField>> finalResult = TapResult.successfully();
        LinkedHashMap<String, TapField> targetFieldMap = new LinkedHashMap<>();
        TapField largestStringMappingField = null;
        TapString cachedTapString = null;

        for(Map.Entry<String, TapField> entry : sourceFields.entrySet()) {
            TapField field = entry.getValue();

            if(field.getTapType() == null)
                throw new CoreException(TapAPIErrorCodes.ERROR_TAP_TYPE_MISSING_ON_FIELD, "Tap type is missing for field {}", field);

            String dataType = null;
            //User custom codec
            if(dataType == null && field.getTapType() != null) {
                dataType = targetCodecFilterManager.getDataTypeByTapType(field.getTapType().getClass());
//                if(dataType != null)
//                    dataType = dataType.toLowerCase();
            }

            //Find best codec
            if(dataType == null) {
                TapResult<String> result = calculateBestTypeMapping(field, targetMatchingMap);
                if(result != null) {
                    dataType = result.getData();
                    List<ResultItem> resultItems = result.getResultItems();
                    if(resultItems != null) {
                        for(ResultItem resultItem : resultItems) {
                            resultItem.setItem(resultItem.getItem() + "@" + field.getName());
                            finalResult.addItem(resultItem);
                        }
                    }
                }
            }

            //handle by default, find largest string type as default
            if(dataType == null) {
                if(largestStringMappingField == null) {
                    cachedTapString = new TapString();
                    largestStringMappingField = findLargestStringType(field, targetMatchingMap, cachedTapString);
                }
                if(largestStringMappingField != null) {
                    UnsupportedTypeFallbackHandler unsupportedTypeFallbackHandler = InstanceFactory.instance(UnsupportedTypeFallbackHandler.class);
                    if(unsupportedTypeFallbackHandler != null && targetCodecFilterManager.getCodecsRegistry() != null) {
                        unsupportedTypeFallbackHandler.handle(targetCodecFilterManager.getCodecsRegistry(), field, largestStringMappingField.getDataType(), cachedTapString);
                    }
                }
            }
            String largestStringDataType = largestStringMappingField != null ? largestStringMappingField.getDataType() : null;
            targetFieldMap.put(field.getName(), dataType == null ? field.clone().dataType(largestStringDataType)
                    : field.clone().dataType(dataType));
        }
        if(finalResult.getResultItems() != null && !finalResult.getResultItems().isEmpty()) {
            finalResult.result(TapResult.RESULT_SUCCESSFULLY_WITH_WARN);
        }
        return finalResult.data(targetFieldMap);
    }

    private TapField findLargestStringType(TapField originField, DefaultExpressionMatchingMap targetMatchingMap, TapString tapString) {
        if(targetMatchingMap == null || targetMatchingMap.isEmpty())
            return null;
        HitTapMappingContainer hitTapMapping = new HitTapMappingContainer();
        TapField field = originField.clone();//new TapField().tapType(tapString).dataType("LargestString");
        field.tapType(tapString);
        targetMatchingMap.iterate(expressionValueEntry -> {
            TapMapping tapMapping = (TapMapping) expressionValueEntry.getValue().get(TapMapping.FIELD_TYPE_MAPPING);
            if(tapMapping != null && tapMapping.getTo() != null) {
                if(tapMapping.getQueryOnly() != null && tapMapping.getQueryOnly()) {
                    return false;
                }
                if(tapMapping.getTo().equals(tapString.getClass().getSimpleName())) {
                    BigDecimal score = tapMapping.matchingScore(field);
                    if(score.compareTo(BigDecimal.ZERO) > 0) {
                        hitTapMapping.input(expressionValueEntry.getKey(), tapMapping, score);
//                        if(score > hitTapMapping.score) {
//                            hitTapMapping.score = score;
//                            hitTapMapping.hitExpression = expressionValueEntry.getKey();
//                            hitTapMapping.tapMapping = tapMapping;
//                        }
                    }
                }
                return false;
            }
            return false;
        });
        HitTapMapping bestOne = hitTapMapping.getBestOne();
        if(bestOne == null)
            return null;
        TapResult<String> result = bestOne.tapMapping.fromTapType(bestOne.hitExpression, field.getTapType());
        List<ResultItem> resultItems = result.getResultItems();
        if(resultItems != null && !resultItems.isEmpty()) {
            for(ResultItem resultItem : resultItems) {
                TapLogger.debug(TAG, "findLargestStringMapping " + resultItem.getItem() + ": " + resultItem.getInformation());
            }
        }
        field.setDataType(result.getData());
        return field;
    }

    static class HitTapMapping {
        String hitExpression;
        TapMapping tapMapping;
        BigDecimal score = TapMapping.MIN_SCORE;

        public HitTapMapping(String hitExpression, TapMapping tapMapping, BigDecimal score) {
            this.hitExpression = hitExpression;
            this.tapMapping = tapMapping;
            this.score = score;
        }
    }

    static class HitTapMappingContainer {
        TreeMap<Integer, HitTapMapping> sortedMap = new TreeMap<>();
        HitTapMapping bestOne = null;

        void input(String hitExpression, TapMapping tapMapping, BigDecimal score) {
            if(bestOne == null || score.compareTo(bestOne.score) > 0) {
                sortedMap.clear();
                bestOne = new HitTapMapping(hitExpression, tapMapping, score);
                sortedMap.put(tapMapping.getPriority(), bestOne);
            } else if(score.equals(bestOne.score)) {
                sortedMap.put(tapMapping.getPriority(), new HitTapMapping(hitExpression, tapMapping, score));
            }
        }

        HitTapMapping getBestOne() {
            Map.Entry<Integer, HitTapMapping> entry = sortedMap.firstEntry();
            if(entry != null)
                return entry.getValue();
            return null;
        }
    }

    TapResult<String> calculateBestTypeMapping(TapField field, DefaultExpressionMatchingMap matchingMap) {
        HitTapMappingContainer bestTapMapping = new HitTapMappingContainer();
        HitTapMappingContainer bestNotHitTapMapping = new HitTapMappingContainer();
//        AtomicReference<String> hitExpression = new AtomicReference<>();
//        AtomicReference<TapMapping> tapMappingReference = new AtomicReference<>();
//        AtomicLong bestScore = new AtomicLong(-1);
//        List<Container<BigDecimal, String>> qualifiedList = new ArrayList<>();
//        List<Container<BigDecimal, String>> noneQualifiedList = new ArrayList<>();
        matchingMap.iterate(expressionValueEntry -> {
            TapMapping tapMapping = (TapMapping) expressionValueEntry.getValue().get(TapMapping.FIELD_TYPE_MAPPING);
            if(tapMapping != null) {
                if(tapMapping.getQueryOnly() != null && tapMapping.getQueryOnly()) {
                    return false;
                }

                BigDecimal score = tapMapping.matchingScore(field);
                if(score.compareTo(BigDecimal.ZERO) >= 0) {
//                    qualifiedList.add(new Container<>(score, expressionValueEntry.getKey()));
                    bestTapMapping.input(expressionValueEntry.getKey(), tapMapping, score);
//                    if(score > bestTapMapping.score) {
//                        bestTapMapping.score = score;
//                        bestTapMapping.hitExpression = expressionValueEntry.getKey();
//                        bestTapMapping.tapMapping = tapMapping;
//                    }
                } else {
//                    noneQualifiedList.add(new Container<>(score, expressionValueEntry.getKey()));
                    bestNotHitTapMapping.input(expressionValueEntry.getKey(), tapMapping, score);
//                    if(score > bestNotHitTapMapping.score) {
//                        bestNotHitTapMapping.score = score;
//                        bestNotHitTapMapping.hitExpression = expressionValueEntry.getKey();
//                        bestNotHitTapMapping.tapMapping = tapMapping;
//                    }
                }
                return false;
            }
            return false;
        });
//        qualifiedList.sort((o1, o2) -> o2.getT().compareTo(o1.getT()));
//        noneQualifiedList.sort((o1, o2) -> o2.getT().compareTo(o1.getT()));
//        TapLogger.info(TAG, "Field {} qualified data types {}, not qualified data types {}", field.getDataType(),
//                qualifiedList.stream().map(Container::getP).collect(Collectors.toList()),
//                noneQualifiedList.stream().map(Container::getP).collect(Collectors.toList()));
        HitTapMapping bestOne = bestTapMapping.getBestOne();
        if(bestOne != null && bestOne.tapMapping != null && bestOne.hitExpression != null) {
            return bestOne.tapMapping.fromTapType(bestOne.hitExpression, field.getTapType());
        }
        HitTapMapping notHitBestOne = bestNotHitTapMapping.getBestOne();
        if(notHitBestOne != null && notHitBestOne.tapMapping != null && notHitBestOne.hitExpression != null) {
            TapResult<String> tapResult = notHitBestOne.tapMapping.fromTapType(notHitBestOne.hitExpression, field.getTapType());
            if(tapResult != null)
                tapResult.addItem(new ResultItem("BEST_IN_UNMATCHED", TapResult.RESULT_SUCCESSFULLY_WITH_WARN, "Select best in unmatched TapMapping, " + notHitBestOne.hitExpression));
            return tapResult;
        }
        return null;
    }


}
