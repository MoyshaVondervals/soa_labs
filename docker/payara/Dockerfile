FROM eclipse-temurin:17-jdk
COPY --from=dist payara.zip /tmp/
RUN cd /opt && jar xf /tmp/payara.zip && chmod +x payara6/bin/* payara6/glassfish/bin/* && rm /tmp/payara.zip
COPY configure.sh entrypoint.sh /opt/
RUN bash /opt/configure.sh
EXPOSE 8181
CMD ["bash", "/opt/entrypoint.sh"]
