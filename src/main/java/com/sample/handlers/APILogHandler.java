package com.sample.handlers;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.synapse.AbstractSynapseHandler;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.wso2.carbon.apimgt.gateway.APIMgtGatewayConstants;

public class APILogHandler extends AbstractSynapseHandler {

    private String apiName = null;
    private String apiCTX = null;
    private String apiMethod = null;
    private String apiTo = null;
    private String apiElectedRsrc = null;
    private String apiRestReqFullPath = null;
    private String apiResponseSC = null;
    private String applicationName = null;
    private String apiConsumerKey = null;
    private String sourceIP = null;

    private static final String UUID_HEADER = "LOG_UUID_HEADER";
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

        apiName = (String) messageContext.getProperty("SYNAPSE_REST_API");
        apiCTX = (String) messageContext.getProperty("REST_API_CONTEXT");
        apiMethod = (String) axis2MsgContext.getProperty("HTTP_METHOD");
        apiElectedRsrc = (String) messageContext.getProperty("API_ELECTED_RESOURCE");
        apiRestReqFullPath = (String) messageContext.getProperty("REST_FULL_REQUEST_PATH");
        sourceIP = getSourceIP(axis2MsgContext, headers);

        return true;
    }

    @Override
    public boolean handleResponseInFlow(MessageContext messageContext) {
        org.apache.axis2.context.MessageContext axis2MsgContext = ((Axis2MessageContext) messageContext)
                .getAxis2MessageContext();

        long responseTime = getResponseTime(messageContext);
        long beTotalLatency = getBackendLatency(messageContext);
        apiResponseSC = String.valueOf(axis2MsgContext.getProperty("HTTP_SC"));
        applicationName = (String) messageContext.getProperty(APIMgtGatewayConstants.APPLICATION_NAME);
        apiConsumerKey = (String) messageContext.getProperty(APIMgtGatewayConstants.CONSUMER_KEY);
        String uuIdHeader = (String) messageContext.getProperty("CORRELATION_ID_HEADER");

        log.info(uuIdHeader + "| Source IP: " + sourceIP + " | API Name: " + apiName + " |" + apiMethod + "| Path: " + apiCTX + apiElectedRsrc
                + " | Response Code: " + apiResponseSC + " | Response Time: " + responseTime + " | Backend Latency: "
                + beTotalLatency);

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
}
