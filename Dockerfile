FROM gcr.io/distroless/java21-debian12
COPY target/altinn_varsel_firewall/app.jar app.jar
COPY target/altinn_varsel_firewall/lib lib
CMD ["app.jar"]
