FROM openjdk:11-jre

ADD build/libs/provenance-explorer*.jar /provenance-explorer.jar
ADD docker/configure /configure

EXPOSE 8612/tcp

ENTRYPOINT ["/configure"]
CMD ["java", "$JAVA_OPTS", "-jar", "/provenance-explorer.jar"]