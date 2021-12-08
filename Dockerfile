FROM navikt/java:15
COPY target/altinn_varsel_firewall/app.jar app.jar
COPY target/altinn_varsel_firewall/lib lib