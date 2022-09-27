package io.tapdata.zoho.service.zoho;

import io.tapdata.entity.error.CoreException;
import io.tapdata.entity.logger.TapLogger;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.entity.utils.cache.KVMap;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.zoho.entity.ContextConfig;
import io.tapdata.zoho.entity.RefreshTokenEntity;
import io.tapdata.zoho.utils.Checker;

public abstract class ZoHoStarter {
    private static final String TAG = ZoHoStarter.class.getSimpleName();

    protected TapConnectionContext tapConnectionContext;
    protected boolean isVerify;
    protected ZoHoStarter(TapConnectionContext tapConnectionContext){
        this.tapConnectionContext = tapConnectionContext;
        this.isVerify = Boolean.FALSE;
    }

    /**
     * 校验connectionConfig配置字段
     */
    public void verifyConnectionConfig(){
        if(this.isVerify){
            return;
        }
        if (null == this.tapConnectionContext){
            throw new IllegalArgumentException("TapConnectorContext cannot be null");
        }
        DataMap connectionConfig = this.tapConnectionContext.getConnectionConfig();
        if (null == connectionConfig ){
            throw new IllegalArgumentException("TapTable' DataMap cannot be null");
        }
        String refreshToken = connectionConfig.getString("refreshToken");
        String accessToken = connectionConfig.getString("accessToken");
        accessToken = accessToken.startsWith(ZoHoBase.ZO_HO_ACCESS_TOKEN_PREFIX)?accessToken:ZoHoBase.ZO_HO_ACCESS_TOKEN_PREFIX+accessToken;
        String clientId = connectionConfig.getString("clientID");
        String clientSecret = connectionConfig.getString("clientSecret");
        String orgId = connectionConfig.getString("orgId");
        String generateCode = connectionConfig.getString("generateCode");
        if ( null == refreshToken || "".equals(refreshToken)){
            TapLogger.debug(TAG, "Connection parameter exception: {} ", refreshToken);
        }
        if ( null == clientId || "".equals(clientId) ){
            TapLogger.debug(TAG, "Connection parameter exception: {} ", clientId);
        }
        if ( null == clientSecret || "".equals(clientSecret) ){
            TapLogger.debug(TAG, "Connection parameter exception: {} ", clientSecret);
        }
        if ( null == generateCode || "".equals(generateCode) ){
            TapLogger.debug(TAG, "Connection parameter streamReadType exception: {} ", generateCode);
        }
        if ( null == accessToken || "".equals(accessToken) ){
            TapLogger.debug(TAG, "Connection parameter connectionMode exception: {} ", accessToken);
        }
        this.isVerify = Boolean.TRUE;
    }
    public ContextConfig veryContextConfigAndNodeConfig(){
        this.verifyConnectionConfig();
        DataMap connectionConfigConfigMap = this.tapConnectionContext.getConnectionConfig();

        String refreshToken = connectionConfigConfigMap.getString("refreshToken");
        String accessToken = connectionConfigConfigMap.getString("accessToken");
        accessToken = accessToken.startsWith(ZoHoBase.ZO_HO_ACCESS_TOKEN_PREFIX)?accessToken:ZoHoBase.ZO_HO_ACCESS_TOKEN_PREFIX+accessToken;
        String clientId = connectionConfigConfigMap.getString("clientID");
        String clientSecret = connectionConfigConfigMap.getString("clientSecret");
        String orgId = connectionConfigConfigMap.getString("orgId");
        String generateCode = connectionConfigConfigMap.getString("generateCode");
        ContextConfig config = ContextConfig.create().refreshToken(refreshToken)
                .accessToken(accessToken)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .orgId(orgId)
                .generateCode(generateCode);
        if (this.tapConnectionContext instanceof TapConnectorContext) {
            KVMap<Object> stateMap = ((TapConnectorContext) this.tapConnectionContext).getStateMap();
            if (null != stateMap) {
                Object refreshTokenObj = stateMap.get("refreshToken");
                Object accessTokenObj = stateMap.get("accessToken");
                if ( Checker.isEmpty(refreshTokenObj) ){
                    stateMap.put("refreshToken",refreshToken);
                    stateMap.put("accessToken",accessToken);
                }
            }
        }
        return config;
    }

    /**取AccessToken*/
    public String accessTokenFromConfig(){
        String accessToken = null;
        if (tapConnectionContext instanceof TapConnectorContext){
            TapConnectorContext connectorContext = (TapConnectorContext) this.tapConnectionContext;
            KVMap<Object> stateMap = connectorContext.getStateMap();
            accessToken = Checker.isNotEmpty(stateMap)?(String)(Checker.isEmpty(stateMap.get("accessToken"))?"":stateMap.get("accessToken")):"";
        }
        if(Checker.isEmpty(accessToken)){
            DataMap connectionConfig = tapConnectionContext.getConnectionConfig();
            Object accessTokenObj = connectionConfig.get("accessToken");
            accessToken = Checker.isNotEmpty(accessTokenObj)?(String)accessTokenObj:"";
        }
        return ZoHoBase.builderAccessToken(accessToken);
    }
    /**取refreshToken*/
    public String refreshTokenFromConfig(){
        DataMap connectionConfig = tapConnectionContext.getConnectionConfig();
        Object refreshTokenObj = connectionConfig.get("refreshToken");
        return Checker.isNotEmpty(refreshTokenObj)?(String)refreshTokenObj:"";
    }
    /**添加accessToken和refreshToken到stateMap*/
    public void addTokenToStateMap(){
        if (Checker.isEmpty(this.tapConnectionContext) || !(this.tapConnectionContext instanceof TapConnectorContext)){
            return;
        }
        ContextConfig contextConfig = this.veryContextConfigAndNodeConfig();
        TapConnectorContext connectorContext = (TapConnectorContext)this.tapConnectionContext;
        KVMap<Object> stateMap = connectorContext.getStateMap();
        stateMap.put("refreshToken",contextConfig.refreshToken());
        String accessToken = contextConfig.accessToken();
        stateMap.put("accessToken",ZoHoBase.builderAccessToken(accessToken));
    }
    public void addNewAccessTokenToStateMap(String accessToken){
        if (Checker.isEmpty(this.tapConnectionContext) || !(this.tapConnectionContext instanceof TapConnectorContext)){
            return;
        }
        TapConnectorContext connectorContext = (TapConnectorContext)this.tapConnectionContext;
        KVMap<Object> stateMap = connectorContext.getStateMap();
        stateMap.put("accessToken",accessToken);
    }
    /**刷新AccessToken并返回AccessToken*/
    public String refreshAndBackAccessToken(){
        RefreshTokenEntity refreshTokenEntity = TokenLoader.create(tapConnectionContext).refreshToken();
        String accessToken = refreshTokenEntity.getAccessToken();
        if (Checker.isEmpty(accessToken)){
            throw new CoreException("Refresh accessToken failed.");
        }
        return ZoHoBase.builderAccessToken(accessToken);
    }

}