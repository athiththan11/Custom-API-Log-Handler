package com.sample.handlers;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.AbstractSynapseHandler;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.wso2.carbon.apimgt.gateway.APIMgtGatewayConstants;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.user.api.ClaimManager;
import org.wso2.carbon.user.api.ClaimMapping;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;

public class APILogHandler extends AbstractSynapseHandler {

    private String apiName = null;
    private String apiVersion = null;
    private String apiCTX = null;
    private String apiMethod = null;
    private String apiTo = null;
    private String apiElectedRsrc = null;
    private String apiRestReqFullPath = null;
    private String apiResponseSC = null;
    private String applicationName = null;
    private String apiConsumerKey = null;
    private String sourceIP = null;
    private String apiCreator = null;
    private String username = null;
    private String tenantDomain = null;
    private String organization = null;
    private SortedMap<String, String> claims;

    private static final String DIALECT_URI = "http://wso2.org/claims";
    private static final String HEADER_X_FORWARDED_FOR = "X-FORWARDED-FOR";

    private static final Log log = LogFactory.getLog(APILogHandler.class);

    @Override
    public boolean handleRequestInFlow(MessageContext messageContext) {
        org.apache.axis2.context.MessageContext axis2MsgContext = ((Axis2MessageContext) messageContext)
                .getAxis2MessageContext();

        apiTo = (String) axis2MsgContext.getProperty("REST_URL_POSTFIX");
        return true;
    }

    @Override
    public boolean handleRequestOutFlow(MessageContext messageContext) {
        org.apache.axis2.context.MessageContext axis2MsgContext = ((Axis2MessageContext) messageContext)
                .getAxis2MessageContext();

        Map headers = (Map) ((Axis2MessageContext) messageContext).getAxis2MessageContext()
                .getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        sourceIP = getSourceIP(axis2MsgContext, headers);

        apiName = (String) messageContext.getProperty("SYNAPSE_REST_API");
        apiVersion = (String) messageContext.getProperty(APIMgtGatewayConstants.API_VERSION);
        apiCTX = (String) messageContext.getProperty(APIMgtGatewayConstants.CONTEXT);
        apiMethod = (String) axis2MsgContext.getProperty(APIMgtGatewayConstants.HTTP_METHOD);
        apiElectedRsrc = (String) messageContext.getProperty("API_ELECTED_RESOURCE");
        apiRestReqFullPath = (String) messageContext.getProperty("REST_FULL_REQUEST_PATH");
        username = (String) messageContext.getProperty(APIMgtGatewayConstants.USER_ID);
        applicationName = (String) messageContext.getProperty(APIMgtGatewayConstants.APPLICATION_NAME);
        apiConsumerKey = (String) messageContext.getProperty(APIMgtGatewayConstants.CONSUMER_KEY);
        apiCreator = (String) messageContext.getProperty(APIMgtGatewayConstants.API_PUBLISHER);

        tenantDomain = MultitenantUtils.getTenantDomainFromRequestURL(apiCTX);
        if (tenantDomain == null) {
            tenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
        }

        /**
         * segment to retrieve the user claims.
         * 
         * @note: Enabling the following portion adds an overhead to the API execution
         *        flow. Enhance the implementation with caches to reduce the latencies
         */

        try {
            claims = getUserClaim(username.split("@")[0]);
            organization = "-";
            if (claims != null && !claims.isEmpty() && claims.containsKey(DIALECT_URI + "organization")) {
                organization = claims.get(DIALECT_URI + "organization");
            }
        } catch (UserStoreException e) {
            log.error("Error while retrieving user claims.", e);
        }

        return true;
    }

    @Override
    public boolean handleResponseInFlow(MessageContext messageContext) {
        org.apache.axis2.context.MessageContext axis2MsgContext = ((Axis2MessageContext) messageContext)
                .getAxis2MessageContext();

        long responseTime = getResponseTime(messageContext);
        long beTotalLatency = getBackendLatency(messageContext);
        apiResponseSC = String.valueOf(axis2MsgContext.getProperty("HTTP_SC"));
        String uuIdHeader = (String) messageContext.getProperty("CORRELATION_ID_HEADER");

        log.info(uuIdHeader + "|" + tenantDomain + "| Username: " + username + " | Organization: " + organization
                + " | Source IP: " + sourceIP + " | API Name: " + apiName + " | API Provider: " + apiCreator + " |"
                + apiMethod + "| Path: " + apiCTX + apiElectedRsrc + " | Response Code: " + apiResponseSC
                + " | Response Time: " + responseTime + " | Backend Latency: " + beTotalLatency);

        return true;
    }

    @Override
    public boolean handleResponseOutFlow(MessageContext synCtx) {
        return true;
    }

    private long getResponseTime(org.apache.synapse.MessageContext messageContext) {
        long responseTime = 0;
        try {
            long rtStartTime = 0;
            if (messageContext.getProperty(APIMgtGatewayConstants.REQUEST_EXECUTION_START_TIME) != null) {
                Object objRtStartTime = messageContext.getProperty(APIMgtGatewayConstants.REQUEST_EXECUTION_START_TIME);
                rtStartTime = (objRtStartTime == null ? 0 : Long.parseLong((String) objRtStartTime));
            }
            responseTime = System.currentTimeMillis() - rtStartTime;
        } catch (Exception e) {
            log.error("Error getResponseTime -  " + e.getMessage(), e);
        }
        return responseTime;
    }

    private long getBackendLatency(org.apache.synapse.MessageContext messageContext) {
        long beTotalLatency = 0;
        long beStartTime = 0;
        long beEndTime = 0;
        long executionStartTime = 0;
        try {
            if (messageContext.getProperty(APIMgtGatewayConstants.BACKEND_REQUEST_END_TIME) == null) {
                if (messageContext.getProperty(APIMgtGatewayConstants.BACKEND_REQUEST_START_TIME) != null) {
                    executionStartTime = Long.parseLong(
                            (String) messageContext.getProperty(APIMgtGatewayConstants.BACKEND_REQUEST_START_TIME));
                }
                messageContext.setProperty(APIMgtGatewayConstants.BACKEND_LATENCY,
                        System.currentTimeMillis() - executionStartTime);
                messageContext.setProperty(APIMgtGatewayConstants.BACKEND_REQUEST_END_TIME, System.currentTimeMillis());
            }
            if (messageContext.getProperty(APIMgtGatewayConstants.BACKEND_REQUEST_START_TIME) != null) {
                beStartTime = Long.parseLong(
                        (String) messageContext.getProperty(APIMgtGatewayConstants.BACKEND_REQUEST_START_TIME));
            }
            if (messageContext.getProperty(APIMgtGatewayConstants.BACKEND_REQUEST_END_TIME) != null) {
                beEndTime = (Long) messageContext.getProperty(APIMgtGatewayConstants.BACKEND_REQUEST_END_TIME);
            }
            beTotalLatency = beEndTime - beStartTime;

        } catch (Exception e) {
            log.error("Error getBackendLatency -  " + e.getMessage(), e);
        }
        return beTotalLatency;
    }

    private String getSourceIP(org.apache.axis2.context.MessageContext axis2Context, Map headers) {
        String clientIP;
        String xForwardedForHeader = (String) headers.get(HEADER_X_FORWARDED_FOR);
        if (!StringUtils.isEmpty(xForwardedForHeader)) {
            clientIP = xForwardedForHeader;
            int index = xForwardedForHeader.indexOf(',');
            if (index > -1) {
                clientIP = clientIP.substring(0, index);
            }
        } else {
            clientIP = (String) axis2Context.getProperty(org.apache.axis2.context.MessageContext.REMOTE_ADDR);
        }

        return clientIP;
    }

    private SortedMap<String, String> getUserClaim(String username) throws UserStoreException {

        RealmService realm = (RealmService) PrivilegedCarbonContext.getThreadLocalCarbonContext()
                .getOSGiService(RealmService.class, null);
        int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();

        SortedMap<String, String> claimValues;
        ClaimManager claimManager = realm.getTenantUserRealm(tenantId).getClaimManager();
        ClaimMapping[] claims = claimManager.getAllClaimMappings(DIALECT_URI);

        String[] claimURIs = new String[claims.length];
        for (int i = 0; i < claims.length; i++) {
            claimURIs[i] = claims[i].getClaim().getClaimUri();
        }

        UserStoreManager userStoreManager = realm.getTenantUserRealm(tenantId).getUserStoreManager();
        claimValues = new TreeMap(userStoreManager.getUserClaimValues(username, claimURIs, null));

        return claimValues;
    }
}
