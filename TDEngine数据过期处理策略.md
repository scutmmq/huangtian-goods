# TDengine 数据过期处理策略

## 一、修改业务数据库（device_snapshot）的 KEEP

- 使用脚本计算参数后在config.properties配置
```config
datasource.tdengine.keep=OourResult
datasource.tdengine.duration=OurResult
```

**解决的是：**

- 业务数据历史保留策略
- 防止客户设备长期运行导致 TSDB 数据无限增长

### 二、修改 log 数据库 的 KEEP

- 执行指令
```sh
docker exec -i tdengine taos < /data/modules/tdenginednode/init/init.sql
```

**解决的是：**

- TDengine 内部监控 / 系统日志 / 运行态噪音
- 这是磁盘爆满的最大来源

**特点：**

- log 数据几乎没有业务价值
- 默认 KEEP 非常激进（90d）

### 三、Docker 环境变量 TAOS_LOG_KEEP_DAYS=1
```bash
-e TAOS_LOG_KEEP_DAYS=1
```

**解决的是：**

- /var/log/taos 目录下的文本日志
- taosdlog.* / taosadapter / keeper 等

**特点：**

- 不影响 TDengine 的 log 数据库
- 只影响文件日志
- 是一个运维兜底项

### TDEngine部署方法

1. **拉取镜像**
```sh
docker pull tdengine/tdengine:3.3.3.0
```

2. **创建容器**
```sh
docker run -d --name tdengine -e TAOS_LOG_KEEP_DAYS=1 -v /data/modules/tdenginednode/data:/var/lib/taos -p 6030:6030 -p 6041:6041 -p 6043-6060:6043-6060 -p 6043-6060:6043-6060/udp tdengine/tdengine:3.3.3.0
```

3. **修改log参数**
```sh
docker exec -i tdengine taos < /data/modules/tdenginednode/init/init.sql
```

4. **进入容器**
```sh
docker exec -it tdengine taos
```

5. **创建用户**
```sh
create user xlink pass 'xlink!@#' sysinfo 1 createdb 1
```