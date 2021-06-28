# API Log Handler

A Custom Synapse Handler to Log API Request / Response in WSO2 API Manager platform.

> The branch contains the source code of the handler implemented for APIM v4.0.0. Please make a clone of this repo and update the dependencies and build the handler to support in other versions of the WSO2 API Manager.

## Build

Execute the following command from the root directory of the project to build

```sh
mvn clean package
```

## Usage

### Configuration

- Copy the built JAR artifact and place it inside the `<gateway>/repository/components/lib` directory and start the server to load the required classes
- Add the following configuration at the beginning of the `<gateway>/repository/conf/deployment.toml` to engage the API Log Handler

  ```toml
  [synapse_handlers.api_log_handler]
  enabled = true
  class= "com.sample.handlers.APILogHandler"
  ```

- Add the following in `<gateway>/repository/conf/log4j2.properties`
  
  > Following to enable the logs to populate under default `wso2carbon.log`. You can create a custom appender to log the entries to a separate log file.
  
  ```properties
  loggers = api-log-handler, AUDIT_LOG, ...

  logger.api-log-handler.name = com.sample.handlers.APILogHandler
  logger.api-log-handler.level = DEBUG
  logger.api-log-handler.appenderRef.CARBON_LOGFILE.ref = CARBON_LOGFILE
  ```

### Log Output

The logs are printed with the following format. A sample log output is given below.

```log
`correlation-id` | Source IP: `source-ip` | API Name: `api-name` |`http-method`| Path: `api-request-path` | Response Code: `response-status-code` | Response Time: `response-time` | Backend Latency: `latency`
```

```log
INFO {com.sample.handlers.APILogHandler} - 1255465765212 | Source IP: 127.0.0.1 | API Name: admin--MockAPI:v1.0.0 |GET| Path: /mock/1.0.0/* | Response Code: 200 | Response Time: 920 | Backend Latency: 828
```

## License

[Apache-2.0](LICENSE)
