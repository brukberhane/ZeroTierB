# ZeroTier-Pylon gap closure plan

## libzt (submodule patches + AAR rebuild)

- [x] JNI query addr/route CIDR strings (managed routes)
- [x] JNI query DNS domain + servers from network config
- [x] `zts_net_set_settings` before join (allowManaged/Default/Global)

## App core

- [x] `ZtNetworkQuery` wrapper with core lock
- [x] Populate routes/DNS in `ZeroTierNodeManager`
- [x] `RouteResolver` longest-prefix + allow* filtering
- [x] `allowDns` gates DNS resolver population

## System integration

- [x] Shizuku grant button (reflection on `Shizuku.newProcess`)
- [x] Boot receiver + start-on-boot setting
- [x] Separate proxy toggle (node can stay up)

## UI / service

- [x] Per-network status map in service state
- [x] Runtime join/leave network actions
- [x] Settings: start on boot, proxy-only toggle
- [x] Per-network addrs/routes/DNS shown in network list

## Deferred

- SOCKS5 BIND/UDP-ASSOCIATE
- Automated tests
- libzt stack-level `allowDNS` (no NetworkSettings field in libzt)
