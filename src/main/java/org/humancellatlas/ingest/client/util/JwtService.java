package org.humancellatlas.ingest.client.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class JwtService {
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    @Autowired
    JwtCredentialsProperties jwtCredentialsProperties;

    @Value("${JWT_EXPIRATION_MS:3600000}") // 1h
    private int jwtExpirationMs;

    @Value("${INGEST_API_JWT_AUDIENCE:https://dev.data.humancellatlas.org/}")
    private String jwtAudience;
    private String token;


    public String getToken() throws Exception {
        if (token == null || isTokenExpired()) {
            generateToken();
        }
        return token;
    }

    public void generateToken() throws Exception {
        final Map<String, Object> payload = Stream.of(
                new Object[]{"https://auth.data.humancellatlas.org/email", jwtCredentialsProperties.getClientEmail()},
                new Object[]{"https://auth.data.humancellatlas.org/group", "hca"},
                new Object[]{"scope", new String[]{"openid", "email", "offline_access"}}
        ).collect(Collectors.toMap(
                data -> (String) data[0],
                data -> data[1]
        ));

        final Key jwtSecret = getSignKey();

        token = Jwts.builder()
                .setIssuer(jwtCredentialsProperties.getClientEmail())
                .setSubject(jwtCredentialsProperties.getClientEmail())
                .setAudience(jwtAudience)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .addClaims(payload)
                .setHeaderParam("kid", jwtCredentialsProperties.getPrivateKeyId())
                .signWith(SignatureAlgorithm.RS256, jwtSecret)
                .compact();
    }


    private Boolean isTokenExpired() throws Exception {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) throws Exception {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) throws Exception {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) throws Exception{
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        try {
            String pemContent = jwtCredentialsProperties.getPrivateKey(); // Remove whitespaces
            String privateKeyString = pemContent.replaceAll("\\s*-----BEGIN PRIVATE KEY-----\\s*", "")
                    .replaceAll("\\s*-----END PRIVATE KEY-----\\s*", "");

//            byte[] decodedBytes = Base64.getDecoder().decode(privateKeyString);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(pemContent.getBytes());
            return RsaKeyConverters.pkcs8().convert(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing private key", e);
        }
    }
//    public String getUserNameFromJwtToken(String token) {
//        return Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token).getBody().getSubject();
//    }

//    public boolean validateJwtToken(String authToken) {
//        try {
//            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken);
//            return true;
//        } catch (SignatureException e) {
//            logger.error("Invalid JWT signature: {}", e.getMessage());
//        } catch (MalformedJwtException e) {
//            logger.error("Invalid JWT token: {}", e.getMessage());
//        } catch (ExpiredJwtException e) {
//            logger.error("JWT token is expired: {}", e.getMessage());
//        } catch (UnsupportedJwtException e) {
//            logger.error("JWT token is unsupported: {}", e.getMessage());
//        } catch (IllegalArgumentException e) {
//            logger.error("JWT claims string is empty: {}", e.getMessage());
//        }
//
//        return false;
//    }

}