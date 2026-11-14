package com.rafaga.backend;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.port.out.ObjectStorage;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test that creates two stores, each uploads 2 products with solid-color
 * JPEG images, processes them to WebP, and validates the complete media flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaE2ETest {

    private static final String TENANT_A = "media-e2e-a";
    private static final String TENANT_B = "media-e2e-b";
    private static final String HOST_A = "a.media-e2e.test";
    private static final String HOST_B = "b.media-e2e.test";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TenantRegistry tenants;
    @Autowired private ObjectStorage objectStorage;

    @BeforeEach
    void registerTenants() {
        tenants.register(tenant(TENANT_A, HOST_A));
        tenants.register(tenant(TENANT_B, HOST_B));
        tenants.findById(TENANT_A);
        tenants.findById(TENANT_B);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void twoStoresEachUploadTwoProductsWithImages() throws Exception {
        // --- Tenant A: create 2 products ---
        String productA1 = createProduct(TENANT_A, HOST_A, "Red Product A1");
        String productA2 = createProduct(TENANT_A, HOST_A, "Blue Product A2");

        // --- Tenant B: create 2 products ---
        String productB1 = createProduct(TENANT_B, HOST_B, "Green Product B1");
        String productB2 = createProduct(TENANT_B, HOST_B, "Yellow Product B2");

        // --- Tenant A: upload images for product A1 (red + orange) ---
        List<UUID> assetIdsA1 = uploadAndConfirmImages(TENANT_A, HOST_A,
            List.of(new ColorImage(Color.RED, 800, 600), new ColorImage(Color.ORANGE, 640, 480)));
        assertThat(assetIdsA1).hasSize(2);
        associateImagesToProduct(TENANT_A, HOST_A, productA1, assetIdsA1);

        // --- Tenant A: upload images for product A2 (blue + cyan) ---
        List<UUID> assetIdsA2 = uploadAndConfirmImages(TENANT_A, HOST_A,
            List.of(new ColorImage(Color.BLUE, 1024, 768), new ColorImage(Color.CYAN, 800, 800)));
        assertThat(assetIdsA2).hasSize(2);
        associateImagesToProduct(TENANT_A, HOST_A, productA2, assetIdsA2);

        // --- Tenant B: upload images for product B1 (green + magenta) ---
        List<UUID> assetIdsB1 = uploadAndConfirmImages(TENANT_B, HOST_B,
            List.of(new ColorImage(Color.GREEN, 800, 600), new ColorImage(Color.MAGENTA, 640, 480)));
        assertThat(assetIdsB1).hasSize(2);
        associateImagesToProduct(TENANT_B, HOST_B, productB1, assetIdsB1);

        // --- Tenant B: upload images for product B2 (yellow + white) ---
        List<UUID> assetIdsB2 = uploadAndConfirmImages(TENANT_B, HOST_B,
            List.of(new ColorImage(Color.YELLOW, 1024, 768), new ColorImage(Color.WHITE, 800, 800)));
        assertThat(assetIdsB2).hasSize(2);
        associateImagesToProduct(TENANT_B, HOST_B, productB2, assetIdsB2);

        // --- Verify Tenant A product images via storefront ---
        verifyProductImages(HOST_A, productA1, 2);
        verifyProductImages(HOST_A, productA2, 2);

        // --- Verify Tenant B product images via storefront ---
        verifyProductImages(HOST_B, productB1, 2);
        verifyProductImages(HOST_B, productB2, 2);

        // --- Verify isolation: Tenant A cannot see Tenant B images ---
        MvcResult result = mvc.perform(get("/api/storefront/products/{productId}/images", productB1)
                        .header("Host", HOST_A))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        List<?> images = mapper.readValue(result.getResponse().getContentAsString(), List.class);
        assertThat(images).isEmpty();

        // --- Verify all assets are WebP ---
        verifyAllAssetsAreWebP(TENANT_A, HOST_A);
        verifyAllAssetsAreWebP(TENANT_B, HOST_B);
    }

    /** Creates a product via the catalog API and returns its ID. */
    private String createProduct(String tenantId, String host, String name) throws Exception {
        String sku = "SKU-" + UUID.randomUUID();
        MvcResult result = mvc.perform(post("/api/products")
                        .header("Host", host).with(jwtFor(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"" + name
                                + "\",\"basePrice\":10.00,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    /**
     * Requests upload URLs, uploads solid-color JPEG images to FakeObjectStorage,
     * and confirms the batch. Returns the list of asset IDs.
     */
    private List<UUID> uploadAndConfirmImages(String tenantId, String host,
            List<ColorImage> images) throws Exception {
        // Step 1: Request upload batch
        StringBuilder uploadRequest = new StringBuilder("[");
        for (int i = 0; i < images.size(); i++) {
            if (i > 0) uploadRequest.append(",");
            uploadRequest.append("{\"assetType\":\"PRODUCT\",\"contentType\":\"image/jpeg\",\"estimatedSize\":10000}");
        }
        uploadRequest.append("]");

        MvcResult uploadResult = mvc.perform(post("/api/media/upload/batch")
                        .header("Host", host).with(jwtFor(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadRequest.toString()))
                .andExpect(status().isOk()).andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadBody =
            mapper.readValue(uploadResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) uploadBody.get("items");

        // Step 2: Extract asset IDs and upload keys, then upload JPEG bytes directly
        List<UUID> assetIds = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            String assetIdStr = (String) item.get("assetId");
            String uploadKey = (String) item.get("uploadKey");
            UUID assetId = UUID.fromString(assetIdStr);
            assetIds.add(assetId);

            // Generate solid-color JPEG and upload directly to FakeObjectStorage
            byte[] jpegBytes = createSolidColorJpeg(images.get(i));
            objectStorage.upload(uploadKey, jpegBytes, "image/jpeg");
        }

        // Step 3: Confirm upload batch — triggers WebP processing
        StringBuilder confirmRequest = new StringBuilder("[");
        for (int i = 0; i < assetIds.size(); i++) {
            if (i > 0) confirmRequest.append(",");
            confirmRequest.append("\"").append(assetIds.get(i)).append("\"");
        }
        confirmRequest.append("]");

        mvc.perform(post("/api/media/upload/batch/confirm")
                        .header("Host", host).with(jwtFor(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest.toString()))
                .andExpect(status().isOk());

        return assetIds;
    }

    /** Associates a list of asset IDs to a product. */
    private void associateImagesToProduct(String tenantId, String host,
            String productId, List<UUID> assetIds) throws Exception {
        StringBuilder associateRequest = new StringBuilder("[");
        for (int i = 0; i < assetIds.size(); i++) {
            if (i > 0) associateRequest.append(",");
            boolean isPrimary = (i == 0);
            associateRequest.append("{\"assetId\":\"").append(assetIds.get(i))
                .append("\",\"position\":").append(i)
                .append(",\"isPrimary\":").append(isPrimary).append("}");
        }
        associateRequest.append("]");

        mvc.perform(post("/api/media/products/{productId}/images/batch", productId)
                        .header("Host", host).with(jwtFor(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(associateRequest.toString()))
                .andExpect(status().isOk());
    }

    /** Verifies a product has the expected number of images via storefront. */
    private void verifyProductImages(String host, String productId, int expectedCount)
            throws Exception {
        MvcResult result = mvc.perform(get("/api/storefront/products/{productId}/images", productId)
                        .header("Host", host))
                .andExpect(status().isOk()).andReturn();

        @SuppressWarnings("unchecked")
        List<?> images = mapper.readValue(result.getResponse().getContentAsString(), List.class);
        assertThat(images).hasSize(expectedCount);

        // Verify each image has WebP content type
        for (Object img : images) {
            @SuppressWarnings("unchecked")
            Map<String, Object> imageMap = (Map<String, Object>) img;
            @SuppressWarnings("unchecked")
            Map<String, Object> asset = (Map<String, Object>) imageMap.get("asset");
            assertThat(asset.get("contentType")).isEqualTo("image/webp");
            assertThat(asset.get("status")).isEqualTo("READY");
            assertThat(asset.get("url")).isNotNull().isInstanceOf(String.class);
            assertThat((String) asset.get("url")).isNotEmpty();
            assertThat(asset.get("width")).isNotNull();
            assertThat(asset.get("height")).isNotNull();
        }
    }

    /** Verifies all assets for a tenant are processed to WebP. */
    private void verifyAllAssetsAreWebP(String tenantId, String host) throws Exception {
        MvcResult result = mvc.perform(get("/api/media")
                        .header("Host", host).with(jwtFor(tenantId))
                        .param("status", "READY"))
                .andExpect(status().isOk()).andReturn();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assets =
            mapper.readValue(result.getResponse().getContentAsString(), List.class);

        assertThat(assets).isNotEmpty();
        for (Map<String, Object> asset : assets) {
            assertThat(asset.get("contentType")).isEqualTo("image/webp");
            assertThat(asset.get("status")).isEqualTo("READY");
            assertThat(asset.get("assetType")).isEqualTo("PRODUCT");
        }
    }

    /** Creates a solid-color JPEG image using BufferedImage. */
    private byte[] createSolidColorJpeg(ColorImage colorImage) throws Exception {
        BufferedImage image = new BufferedImage(
            colorImage.width, colorImage.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(colorImage.color);
        g2d.fillRect(0, 0, colorImage.width, colorImage.height);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", baos);
        return baos.toByteArray();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String tenantId) {
        return jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_STORE_MANAGER"));
    }

    private TenantConfig tenant(String id, String domain) {
        return new TenantConfig(id, id, List.of(domain), DeploymentMode.SHARED_DB,
                null, "test", TenantStatus.ACTIVE, Map.of(
                        "catalog", true,
                        "inventory", true,
                        "orders", true,
                        "media", true));
    }

    private record ColorImage(Color color, int width, int height) {}
}
