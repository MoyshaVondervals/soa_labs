FROM eclipse-temurin:17-jdk
COPY --from=dist wildfly.zip postgresql.jar /tmp/
RUN cd /opt && jar xf /tmp/wildfly.zip && mv wildfly-35.0.1.Final wildfly \
    && chmod +x wildfly/bin/*.sh && rm /tmp/wildfly.zip
COPY configure.cli /tmp/configure.cli
RUN TLS_PASSWORD=build-time-placeholder /opt/wildfly/bin/jboss-cli.sh --file=/tmp/configure.cli && rm -rf /opt/wildfly/standalone/configuration/standalone_xml_history
EXPOSE 8443
CMD ["bash", "-c", "cp /deploy/worker.war /opt/wildfly/standalone/deployments/ && exec /opt/wildfly/bin/standalone.sh -b 0.0.0.0"]
