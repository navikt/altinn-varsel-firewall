FROM navikt/java:17
COPY target/altinn_varsel_firewall/app.jar app.jar
COPY target/altinn_varsel_firewall/lib lib
