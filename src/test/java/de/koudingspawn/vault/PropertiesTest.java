package de.koudingspawn.vault;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.google.common.collect.ImmutableMap;
import de.koudingspawn.vault.crd.Vault;
import de.koudingspawn.vault.crd.VaultPropertiesConfiguration;
import de.koudingspawn.vault.crd.VaultSpec;
import de.koudingspawn.vault.crd.VaultType;
import de.koudingspawn.vault.kubernetes.EventHandler;
import de.koudingspawn.vault.kubernetes.scheduler.impl.CertRefresh;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static de.koudingspawn.vault.Constants.LAST_UPDATE_ANNOTATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(
        properties = {
                "kubernetes.vault.url=http://localhost:8208/v1/",
                "kubernetes.initial-delay=5000000",
                "kubernetes.vault.token=c73ab0cb-41e6-b89c-7af6-96b36f1ac87b"
        },
        classes = {
                TestConfiguration.class
        }

)
public class PropertiesTest {

    @ClassRule
    public static WireMockClassRule wireMockClassRule =
            new WireMockClassRule(wireMockConfig().port(8208));

    @Rule
    public WireMockClassRule instanceRule = wireMockClassRule;

    @Autowired
    public EventHandler handler;

    @Autowired
    KubernetesClient client;

    @Autowired
    CertRefresh certRefresh;

    @Before
    public void before() {
        WireMock.resetAllScenarios();
        client.secrets().inAnyNamespace().delete();

        TestHelper.generateLookupSelfStub();
    }

    @Test
    public void shouldRenderPropertiesFile() throws IOException {
        TestHelper.generateKVStup("kv/key", ImmutableMap.of("value", "kv1content"));
        TestHelper.generateKV2Stup("kv2/key", ImmutableMap.of("value", "kv2content"));

        Vault vault = generatePropertiesManifest();
        handler.addHandler(vault);

        Secret secret = client.secrets().inNamespace("default").withName("properties").get();
        assertEquals("properties", secret.getMetadata().getName());
        assertEquals("default", secret.getMetadata().getNamespace());
        assertEquals("Opaque", secret.getType());
        assertNotNull(secret.getMetadata().getAnnotations().get("vault.koudingspawn.de" + LAST_UPDATE_ANNOTATION));
        String renderedB64Properties = secret.getData().get("test.properties");
        String renderedProperties = new String(Base64.getDecoder().decode(renderedB64Properties));

        assertTrue(renderedProperties.contains("test=kv1content"));
        assertTrue(renderedProperties.contains("test2=kv2content"));
        assertTrue(renderedProperties.contains("test3=contextvalue"));
    }

    private Vault generatePropertiesManifest() throws IOException {
        HashMap<String, String> properties = new HashMap<>();
        File file = new ClassPathResource("test.properties").getFile();
        String content = new String(Files.readAllBytes(file.toPath()));
        properties.put("test.properties", content);

        HashMap<String, String> context = new HashMap<>();
        context.put("contextkey", "contextvalue");

        VaultSpec vaultSpec = new VaultSpec();
        vaultSpec.setType(VaultType.PROPERTIES);

        VaultPropertiesConfiguration vaultPropertiesConfiguration = new VaultPropertiesConfiguration();
        vaultPropertiesConfiguration.setFiles(properties);
        vaultPropertiesConfiguration.setContext(context);
        vaultSpec.setPropertiesConfiguration(vaultPropertiesConfiguration);

        Vault vault = new Vault();
        vault.setMetadata(
                new ObjectMetaBuilder().withName("properties").withNamespace("default").build()
        );
        vault.setSpec(vaultSpec);

        return vault;
    }

}

