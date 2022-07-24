package io.tapdata.schema;

import com.tapdata.constant.BeanUtil;
import com.tapdata.constant.ConnectorConstant;
import com.tapdata.mongo.ClientMongoOperator;
import io.tapdata.cache.EhcacheService;
import io.tapdata.entity.schema.TapField;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.pdk.core.utils.CommonUtils;
import io.tapdata.pdk.core.utils.cache.EhcacheKVMap;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Query;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * @author samuel
 * @Description
 * @create 2022-05-10 11:16
 **/
public class TapTableMap<K extends String, V extends TapTable> extends HashMap<K, V> {
	private static final String DIST_CACHE_PATH = "tap_table_ehcache";
	public static final int DEFAULT_OFF_HEAP_MB = 100;
	public static final int DEFAULT_DISK_MB = 1024;
	public static final int MAX_HEAP_ENTRIES = 100;
	public static final String TAP_TABLE_OFF_HEAP_MB_KEY = "TAP_TABLE_OFF_HEAP_MB";
	public static final String TAP_TABLE_DISK_MB_KEY = "TAP_TABLE_DISK_MB";
	public static final String TAP_TABLE_PREFIX = "TAP_TABLE_";
	private Map<K, String> tableNameAndQualifiedNameMap;
	private String mapKey;
	private Lock lock = new ReentrantLock();
	private String nodeId;

	private TapTableMap() {

	}

	public static TapTableMap<String, TapTable> create(String nodeId, Map<String, String> tableNameAndQualifiedNameMap) {
		TapTableMap<String, TapTable> tapTableMap = new TapTableMap<>();
		return tapTableMap
				.nodeId(nodeId)
				.tableNameAndQualifiedNameMap(tableNameAndQualifiedNameMap)
				.init();
	}

	public static TapTableMap<String, TapTable> createNew(String nodeId, Map<String, String> tableNameAndQualifiedNameMap) {
		TapTableMap<String, TapTable> tapTableMap = new TapTableMap<>();
		tapTableMap
				.nodeId(nodeId)
				.tableNameAndQualifiedNameMap(tableNameAndQualifiedNameMap)
				.init();
		EhcacheService.getInstance().getEhcacheKVMap(tapTableMap.mapKey).clear();
		return tapTableMap;
	}

	private TapTableMap<K, V> init() {
		if (StringUtils.isBlank(nodeId)) {
			throw new RuntimeException("Missing node id");
		}
//		if (MapUtils.isEmpty(tableNameAndQualifiedNameMap)) {
//			throw new RuntimeException("Missing table name and qualified name map");
//		}
		this.mapKey = TAP_TABLE_PREFIX + nodeId;
		EhcacheKVMap<TapTable> tapTableMap = EhcacheKVMap.create(mapKey, TapTable.class)
				.cachePath(DIST_CACHE_PATH)
				.maxHeapEntries(MAX_HEAP_ENTRIES)
				.maxOffHeapMB(CommonUtils.getPropertyInt(TAP_TABLE_OFF_HEAP_MB_KEY, DEFAULT_OFF_HEAP_MB))
				.maxDiskMB(CommonUtils.getPropertyInt(TAP_TABLE_DISK_MB_KEY, DEFAULT_DISK_MB))
				.init();
		EhcacheService.getInstance().putEhcacheKVMap(mapKey, tapTableMap);
		return this;
	}

	public void remove() {
		EhcacheService.getInstance().getEhcacheKVMap(mapKey).reset();
		EhcacheService.getInstance().removeEhcacheKVMap(mapKey);
	}

	public String getQualifiedName(String tableName) {
		return tableNameAndQualifiedNameMap.get(tableName);
	}

	private TapTableMap<K, V> tableNameAndQualifiedNameMap(Map<K, String> tableNameAndQualifiedNameMap) {
		this.tableNameAndQualifiedNameMap = new ConcurrentHashMap<>(tableNameAndQualifiedNameMap);
		return this;
	}

	private TapTableMap<K, V> nodeId(String nodeId) {
		this.nodeId = nodeId;
		return this;
	}

	@Override
	public int size() {
		return tableNameAndQualifiedNameMap.size();
	}

	@Override
	public boolean isEmpty() {
		return tableNameAndQualifiedNameMap.isEmpty();
	}

	@Override
	public V get(Object key) {
		return (V) getTapTable((K) key);
	}

	@Override
	public boolean containsKey(Object key) {
		return tableNameAndQualifiedNameMap.containsKey(key);
	}

	@Override
	public V put(K key, V value) {
		if (!tableNameAndQualifiedNameMap.containsKey(key)) {
			throw new IllegalArgumentException("Table " + key + " does not exists, cannot put in table map");
		}
		EhcacheService.getInstance().getEhcacheKVMap(mapKey).put(key, value);
		return value;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V remove(Object key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		this.tableNameAndQualifiedNameMap.clear();
		EhcacheService.getInstance().getEhcacheKVMap(this.mapKey).clear();
	}

	@Override
	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<K> keySet() {
		return tableNameAndQualifiedNameMap.keySet();
	}

	@Override
	public Collection<V> values() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		throw new UnsupportedOperationException();
	}

	@Override
	public V getOrDefault(Object key, V defaultValue) {
		if (containsKey(key)) {
			return get(key);
		} else {
			return defaultValue;
		}
	}

	@Override
	public V putIfAbsent(K key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object key, Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V replace(K key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {
		for (K k : tableNameAndQualifiedNameMap.keySet()) {
			V v = get(k);
			action.accept(k, v);
		}
	}

	@Override
	public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
		throw new UnsupportedOperationException();
	}

	private TapTable getTapTable(K key) {
		EhcacheKVMap<TapTable> tapTableMap = EhcacheService.getInstance().getEhcacheKVMap(this.mapKey);
		AtomicReference<TapTable> tapTable = new AtomicReference<>(tapTableMap.get(key));
		if (null == tapTable.get()) {
			try {
				handleWithLock(() -> {
					tapTable.set(tapTableMap.get(key));
					if (null == tapTable.get()) {
						tapTable.set(findSchema(key));
						tapTableMap.put(key, tapTable.get());
					}
				});
			} catch (Exception e) {
				throw new RuntimeException("Find schema failed, message: " + e.getMessage(), e);
			}
		}
		return tapTable.get();
	}

	private V findSchema(K k) {
		String qualifiedName = tableNameAndQualifiedNameMap.get(k);
		if (StringUtils.isBlank(qualifiedName)) {
			throw new RuntimeException("Table name \"" + k + "\" not exists, qualified name: " + qualifiedName);
		}
		ClientMongoOperator clientMongoOperator = BeanUtil.getBean(ClientMongoOperator.class);
		String url = ConnectorConstant.METADATA_INSTANCE_COLLECTION + "/tapTables";
		Query query = Query.query(where("qualified_name").is(qualifiedName));
		TapTable tapTable = clientMongoOperator.findOne(query, url, TapTable.class);
		LinkedHashMap<String, TapField> nameFieldMap = tapTable.getNameFieldMap();
		if (MapUtils.isNotEmpty(nameFieldMap)) {
			LinkedHashMap<String, TapField> sortedFieldMap = new LinkedHashMap<>();
			nameFieldMap.entrySet().stream().sorted((o1, o2) -> {
				Integer o1Pos = o1.getValue().getPos();
				Integer o2Pos = o2.getValue().getPos();
				if (o1Pos == null && o2Pos == null) {
					return 0;
				}

				if (o1Pos == null) {
					return -1;
				}

				if (o2Pos == null) {
					return 1;
				}
				return o1Pos.compareTo(o2Pos);
			}).forEach(entry -> sortedFieldMap.put(entry.getKey(), entry.getValue()));
			tapTable.setNameFieldMap(sortedFieldMap);
		}
		return (V) tapTable;
	}

	private void handleWithLock(Handler handler) throws Exception {
		try {
			lock();
			handler.run();
		} finally {
			lock.unlock();
		}
	}

	private void lock() throws Exception {
		while (true) {
			if (Thread.currentThread().isInterrupted()) {
				break;
			}
			if (lock.tryLock(3, TimeUnit.SECONDS)) {
				break;
			}
		}
	}

	interface Handler {
		void run() throws Exception;
	}

	public void reset() {
		EhcacheKVMap<TapTable> ehcacheKVMap = EhcacheService.getInstance().getEhcacheKVMap(this.mapKey);
		Optional.ofNullable(ehcacheKVMap).ifPresent(EhcacheKVMap::reset);
	}
}