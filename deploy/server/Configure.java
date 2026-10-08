import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import org.w3c.dom.*;

public class Configure {
  public static void main(String[] args) throws Exception {
    Settings settings = readSettings(args);

    configureWildFly(settings);
    configurePayara(settings);
    updateWarAddresses(settings);

    System.out.println("Configuration ready: " + settings.workerUrl() + " and " + settings.hrUrl());
  }

  private record Settings(
      Path base, String workerUrl, String hrUrl,
      String databaseUrl, String databaseUser, String corsOrigins) {
    Path wildFlyConfig() {
      return base.resolve("wildfly/standalone/configuration/standalone.xml");
    }

    Path payaraConfig() {
      return base.resolve("payara-domains/soa-lab2/config/domain.xml");
    }
  }

  private static Settings readSettings(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("Usage: Configure <installation directory>");
    Path base = Path.of(args[0]).toAbsolutePath();
    String serverHost = env("SOA_SERVER_HOST", null);
    String dbHost = env("SOA_DB_HOST", "pg");
    String dbPort = env("SOA_DB_PORT", "5432");
    String dbName = env("SOA_DB_NAME", "studs");
    String dbUser = env("SOA_DB_USER", null);
    String dbSchema = env("SOA_DB_SCHEMA", dbUser);

    if (!dbSchema.matches("[A-Za-z_][A-Za-z0-9_]*") || !dbPort.matches("[0-9]+")
        || !dbHost.matches("[A-Za-z0-9.-]+") || !dbName.matches("[A-Za-z0-9_-]+")
        || !serverHost.matches("[A-Za-z0-9.-]+")) {
      throw new IllegalArgumentException("Invalid server hostname or database settings");
    }

    String workerUrl = "https://" + serverHost + ":18443";
    String hrUrl = "https://" + serverHost + ":19443";
    String databaseUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName
        + "?currentSchema=" + dbSchema;

    String origins = workerUrl + ",https://localhost:18443";

    return new Settings(base, workerUrl, hrUrl, databaseUrl, dbUser, origins);
  }

  private static void configureWildFly(Settings settings) throws Exception {
    Document document = Xml.read(settings.wildFlyConfig());

    Xml.removeAll(document,
        "/*/*[local-name()='deployments']/*[local-name()='deployment' and @name='worker.war']");
    configureWildFlyHttps(document, settings);
    configureWildFlyProperties(document, settings);
    configureDataSource(document, settings);
    Xml.save(document, settings.wildFlyConfig());
  }

  private static void configureWildFlyHttps(Document document, Settings settings) throws Exception {
    Element store = Xml.one(document, "//*[local-name()='key-store' and @name='applicationKS']");
    Element file = Xml.one(store, "*[local-name()='file']");
    file.setAttribute("path", settings.base().resolve("tls/worker.p12").toString());
    file.removeAttribute("relative-to");
    Xml.one(store, "*[local-name()='credential-reference']")
        .setAttribute("clear-text", "${env.SOA_TLS_PASSWORD}");
    Xml.one(store, "*[local-name()='implementation']").setAttribute("type", "PKCS12");

    Element keyManager = Xml.one(document, "//*[local-name()='key-manager' and @name='applicationKM']");
    keyManager.removeAttribute("generate-self-signed-certificate-host");
    Xml.one(keyManager, "*[local-name()='credential-reference']")
        .setAttribute("clear-text", "${env.SOA_TLS_PASSWORD}");

    Xml.one(document, "//*[local-name()='https-listener' and @name='https']")
        .setAttribute("allow-encoded-slash", "true");
    Xml.removeAll(document, "//*[local-name()='http-listener' and @name='default']");
    for (Element connector :
        Xml.all(document, "//*[local-name()='http-connector' and @connector-ref='default']")) {
      connector.setAttribute("connector-ref", "https");
    }
  }

  private static void configureWildFlyProperties(Document document, Settings settings) throws Exception {
    Element properties = Xml.one(document, "/*/*[local-name()='system-properties']");
    if (properties == null) {
      properties = document.createElementNS(
          document.getDocumentElement().getNamespaceURI(), "system-properties");
      document.getDocumentElement().insertBefore(
          properties, Xml.one(document, "/*/*[local-name()='management']"));
    }
    Xml.setProperties(properties, "property", Map.of(
        "app.cors.origin", settings.corsOrigins(),
        "app.cors.allow.hr.redirect", "true"));
  }

  private static void configureDataSource(Document document, Settings settings) throws Exception {
    Element sources = Xml.one(document, "//*[local-name()='subsystem']/*[local-name()='datasources']");
    Element drivers = Xml.one(sources, "*[local-name()='drivers']");
    Xml.removeAll(sources, "*[local-name()='datasource' and @pool-name='WorkerDS']");

    Xml.removeAll(sources, "*[local-name()='datasource' and @pool-name='ExampleDS']");
    for (Element bindings : Xml.all(document, "//*[local-name()='default-bindings']")) {
      bindings.removeAttribute("datasource");
    }

    if (Xml.one(drivers, "*[local-name()='driver' and @name='postgresql']") == null) {
      Element driver = Xml.add(drivers, "driver");
      driver.setAttribute("name", "postgresql");
      driver.setAttribute("module", "org.postgresql");
      Xml.addText(driver, "driver-class", "org.postgresql.Driver");
    }

    Element source = document.createElementNS(sources.getNamespaceURI(), "datasource");
    source.setAttribute("jndi-name", "java:/jdbc/WorkerDS");
    source.setAttribute("pool-name", "WorkerDS");
    source.setAttribute("enabled", "true");
    Xml.addText(source, "connection-url", settings.databaseUrl());
    Xml.addText(source, "driver", "postgresql");
    Xml.addText(Xml.add(source, "pool"), "max-pool-size", "10");
    Element security = Xml.add(source, "security");
    security.setAttribute("user-name", settings.databaseUser());
    security.setAttribute("password", "${env.SOA_DB_PASSWORD}");
    sources.insertBefore(source, drivers);
  }

  private static void configurePayara(Settings settings) throws Exception {
    Document document = Xml.read(settings.payaraConfig());
    Element config = Xml.one(document, "//config[@name='server-config']");

    Xml.removeAll(document,
        "//applications/application[@name='hr'] | //servers/server/application-ref[@ref='hr']");
    configurePayaraListeners(document, config, settings);
    configurePayaraMemory(document);
    Xml.setProperties(config, "system-property", Map.of(
        "worker.base.url", "https://localhost:18443",

        "worker.public.base.url", env("SOA_PUBLIC_WORKER_URL", "https://localhost:18443"),

        "worker.truststore", settings.base().resolve("tls/worker-trust.p12").toString(),
        "app.cors.origin", settings.corsOrigins()));
    Xml.save(document, settings.payaraConfig());
  }

  private static void configurePayaraListeners(
      Document document, Element config, Settings settings) throws Exception {
    Xml.one(config, ".//network-listener[@name='http-listener-1']").setAttribute("enabled", "false");
    Xml.one(config, ".//network-listener[@name='http-listener-2']")
        .setAttribute("port", "19443");
    Xml.one(config, ".//network-listener[@name='admin-listener']").setAttribute("port", "19448");
    Xml.one(config, ".//protocol[@name='http-listener-2']/ssl").setAttribute("cert-nickname", "soa-hr");
    for (Element listener :
        Xml.all(document, "//network-listener | //iiop-listener | //jmx-connector")) {
      listener.setAttribute("address", "127.0.0.1");
    }
    Xml.one(config, ".//network-listener[@name='http-listener-2']")
        .setAttribute("address", "0.0.0.0");

    for (Element service : Xml.all(document, "//jms-service")) service.setAttribute("type", "DISABLED");
    for (Element host : Xml.all(document, "//jms-host")) host.setAttribute("host", "127.0.0.1");
  }

  private static void configurePayaraMemory(Document document) throws Exception {
    for (Element javaConfig : Xml.all(document, "//java-config")) {
      for (Element option : Xml.all(javaConfig, "jvm-options")) {
        String value = option.getTextContent();
        if (value.startsWith("-Xmx")) option.setTextContent("-Xmx512m");
        if (value.startsWith("-Xms")) option.setTextContent("-Xms128m");
        if (value.startsWith("-XX:ActiveProcessorCount=")) javaConfig.removeChild(option);
      }
      Xml.addText(javaConfig, "jvm-options", "-XX:ActiveProcessorCount=2");
    }
  }

  private static void updateWarAddresses(Settings settings) throws Exception {
    URI archive = URI.create("jar:" + settings.base().resolve("dist/worker.war").toUri());
    try (var zip = FileSystems.newFileSystem(archive, Map.of())) {
      Files.writeString(zip.getPath("/config.json"), "{\"hrPort\":19443}");
      for (String name : List.of("worker-service.yaml", "hr-service.yaml")) {
        Path path = zip.getPath("/docs/" + name);
        String url = name.startsWith("worker") ? settings.workerUrl() : settings.hrUrl();
        String yaml = Files.readString(path).replaceAll(
            "(?m)^  - url: [^\\r\\n]+", Matcher.quoteReplacement("  - url: " + url));
        Files.writeString(path, yaml);
      }
    }
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) value = fallback;
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Set environment variable " + name);
    }
    return value;
  }

  private static class Xml {
    private static final XPath XPATH = XPathFactory.newInstance().newXPath();

    static Document read(Path path) throws Exception {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return factory.newDocumentBuilder().parse(path.toFile());
    }

    static void save(Document document, Path path) throws Exception {
      var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.transform(new DOMSource(document), new StreamResult(path.toFile()));
    }

    static Element one(Node node, String query) throws Exception {
      return (Element) XPATH.evaluate(query, node, XPathConstants.NODE);
    }

    static List<Element> all(Node node, String query) throws Exception {
      var nodes = (NodeList) XPATH.evaluate(query, node, XPathConstants.NODESET);
      var elements = new ArrayList<Element>();
      for (int index = 0; index < nodes.getLength(); index++) {
        elements.add((Element) nodes.item(index));
      }
      return elements;
    }

    static Element add(Element parent, String name) {
      Element element = parent.getOwnerDocument().createElementNS(parent.getNamespaceURI(), name);
      parent.appendChild(element);
      return element;
    }

    static void addText(Element parent, String name, String value) {
      add(parent, name).setTextContent(value);
    }

    static void removeAll(Node parent, String query) throws Exception {
      for (Element element : all(parent, query)) element.getParentNode().removeChild(element);
    }

    static void setProperties(Element parent, String tag, Map<String, String> values) throws Exception {
      for (var entry : values.entrySet()) {
        Element property = one(parent,
            "*[local-name()='" + tag + "' and @name='" + entry.getKey() + "']");
        if (property == null) {
          property = add(parent, tag);
          property.setAttribute("name", entry.getKey());
        }
        property.setAttribute("value", entry.getValue());
      }
    }
  }
}
