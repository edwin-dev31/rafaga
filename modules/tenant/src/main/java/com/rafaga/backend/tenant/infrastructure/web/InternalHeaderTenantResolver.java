package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.tenant.TenantResolver;
import com.rafaga.backend.tenant.infrastructure.config.TenantHeaderProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves X-Tenant-ID only for explicitly enabled, allowlisted internal callers. */
@Component
public class InternalHeaderTenantResolver implements TenantResolver {
    static final String HEADER_NAME = "X-Tenant-ID";
    private final TenantHeaderProperties properties;
    public InternalHeaderTenantResolver(TenantHeaderProperties properties) { this.properties = properties; }
    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        if (!properties.isEnabled() || !isTrustedAddress(request.getRemoteAddr())) return Optional.empty();
        return Optional.ofNullable(request.getHeader(HEADER_NAME)).filter(value -> !value.isBlank());
    }
    private boolean isTrustedAddress(String address) { return properties.getTrustedCidrs().stream().anyMatch(cidr -> contains(cidr, address)); }
    private boolean contains(String cidr, String address) {
        String[] parts = cidr.split("/", 2);
        if (parts.length != 2) return false;
        try {
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            byte[] candidate = InetAddress.getByName(address).getAddress();
            int prefix = Integer.parseInt(parts[1]);
            if (network.length != candidate.length || prefix < 0 || prefix > network.length * Byte.SIZE) return false;
            for (int bit = 0; bit < prefix; bit++) {
                int index = bit / Byte.SIZE;
                int mask = 1 << (Byte.SIZE - 1 - bit % Byte.SIZE);
                if ((network[index] & mask) != (candidate[index] & mask)) return false;
            }
            return true;
        } catch (UnknownHostException | NumberFormatException exception) { return false; }
    }
}
