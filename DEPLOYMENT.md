# Wallet Issuing Service

This backend service for provisioning and revoking verifiable credentials runs at <https://wallet.a-sit.at/m7>.

Some notable configuration properties (aside from the usual setting of context paths, ports and logging) are:

```yaml
backend:
  pki:
    internal:
      key:
        type: MEMORY
  issuer-key:
    type: KEYSTORE
    keystore:
      path: "file:/srv/wallet-backend-m7/data/keystore.p12"
      type: PKCS12
  eprescription:
    url: https://test.baumann.at/sites/ott-service/
    api-key: TODO
spring:
  jpa:
    database: H2
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: "org.hibernate.dialect.H2Dialect"
        jdbc:
          lob:
            non_contextual_creation: true
  datasource:
    driver-class: "org.h2.Driver"
    url: "jdbc:h2:file:/srv/wallet-backend-m7/data/h2.db"
    platform: h2
    username: sa
    password: sa
  security:
    oauth2:
      client:
        registration:
          idaq:
            client-id: "https://wallet.a-sit.at/m7"
            client-secret: "TODO"
            scope: "openid, profile"
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: "https://wallet.a-sit.at/m7/login/oauth2/code/idaq"
            client-name: "ID Austria"
        provider:
          idaq:
            issuer-uri: "https://eid2.oesterreich.gv.at"
```

There are some settings necessary for the reverse proxy, in this case Apache2:

```
<Location "/m7">
  ProxyPass "http://localhost:9860/m7"
  ProxyPassReverse "http://localhost:9860/m7"
  ProxyPreserveHost On
  RequestHeader set X-Forwarded-Proto https
  RequestHeader set X-Forwarded-Port 443
</Location>

Alias /.well-known /var/www/wallet/html/.well-known

<Directory /var/www/wallet/html/.well-known>
  AllowOverride All
</Directory>
```

Inside `/var/www/wallet/html/.well-known` put this `.htaccess` file, to forward requests to well-known URLs:

```
RewriteEngine On

RewriteRule ^jwt-vc-issuer/(.*)$ /$1/.well-known/jwt-vc-issuer [L]
RewriteRule ^mdoc-issuer/(.*)$ /$1/.well-known/jwt-vc-issuer [L]
RewriteRule ^jar-issuer/(.*)$ /$1/.well-known/jwt-vc-issuer [L]
RewriteRule ^oauth-authorization-server/(.*)$ /$1/.well-known/oauth-authorization-server [L]
RewriteRule ^openid-credential-issuer/(.*)$ /$1/.well-known/openid-credential-issuer [L]
RewriteCond %{REQUEST_FILENAME} -d

<Files "apple-app-site-association">
    Header set Content-type 'application/json'
</Files>
<Files "wallet-metadata">
    Header set Content-type 'application/json'
</Files>
<Files "assetlinks.json">
    Header set Content-type 'application/json'
</Files>

Options -Indexes
```

There needs to be self-signed certificate attached to the key used to sign credentials (see above in `backend.issuer-key`).
