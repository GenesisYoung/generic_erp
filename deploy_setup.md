# Deploy setups

## ip ralated

```yaml
# application.yml — only when deployed behind nginx / a load balancer
server:
  forward-headers-strategy: native # Tomcat's RemoteIpValve honours X-Forwarded-For
  tomcat:
    remoteip:
      trusted-proxies: 10\.0\.0\.\d{1,3} # your proxy's address range, nothing else
```
