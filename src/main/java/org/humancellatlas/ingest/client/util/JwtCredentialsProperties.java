package org.humancellatlas.ingest.client.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@Component
@ConfigurationProperties
@Validated
@Getter
@Setter
public class JwtCredentialsProperties {
    @NotBlank
    private String clientEmail;
    @NotBlank
    private String privateKey;
    @NotBlank
    private String privateKeyId;
}
