# `autoexec script` 扩展自 [Flyway](https://flywaydb.org)

flyway的目标是让数据迁移变得容易。 但， `autoexec script` 的目标是在程序启动时自动执行脚本。总之，两个目标不同，所以必要的源码转换是在 `9.1.3` 版本的 `flyway-core` 基础上完成，并会不断更新。

### :rocket: 特性

- (flyway) 执行的脚本有执行记录，包括执行时间和执行状态，用于判断脚本是否已完成并成功执行。
- (flyway) 脚本执行失败后，会回滚当前脚本，直接退出服务。
- (flyway) 在多个节点上部署服务时，相同的脚本只在一个服务节点上执行。
- (flyway) 支持市面上主流的数据库类型。例如PostgreSQL、oracle、MySQL、SybaseASE、SQL Server、Snowflake 等。 [具体支持见链接](https://flywaydb.org/)。
- (flyway) 支持SQL脚本中的变量替换，类似于spring-boot的${xxxx}变量替换。
- (flyway) 当数据库模式中已经存在表信息时，设置脚本会自动执行起始版本。适用于部分地区已经使用autoexec-script组件启动的程序。
- :new: 脚本更改后程序启动时支持重新执行。
- :new: 支持同一版本和类型的多个脚本维护。
- :new: 支持一些本地化数据库。包括，人大金仓 V8R6 以上，优炫 2.1.1.3 以上，达梦 V8 以上。
- :new: 支持只允许执行一次的 SQL 脚本。

#### :watermelon:暂不支持

- 异步执行耗时长的脚本。

### :strawberry: 使用必需前提

- 普通 SQL 脚本文件名格式 `V{version}__{description}.sql`。例如，`V1__01_create.sql`、`V1__02_init.sql`。
- 仅执行一次 SQL 脚本文件名格式 `X{version}__{description}.sql`。例如，`X1__03_correctonce.sql`。
- SQL 脚本建议为可重复执行脚本（除仅执行一次 SQL 脚本外）。
- `autoexec script` 配置中常用配置的建议值为必需配置。

### :lemon: 集成之路

1. maven 项目引 `autoexec script` 入项目
> jar 包交由 github 管理，引入前请阅读[链接](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#installing-a-package)

```xml
<properties>
	<version.autoexeccore>版本号</version.autoexeccore>
</properties>
<dependencies>
   <dependency>
     <groupId>beluga.autoexec</groupId>
     <artifactId>autoexec-core</artifactId>
     <version>${version.autoexeccore}</version>
  </dependency>
</dependencies>
```
2. `autoexec script` 配置

| 配置名                               | 配置说明                                                     | 建议值                        |
| ------------------------------------ | ------------------------------------------------------------ | ----------------------------- |
| `autoexec.schemas`                   | 服务涉及 `schemas` 范围，程序会在第一个 `schema` 下创建 `flyway_schema_history` 脚本执行记录表 | 具体 `schema` 名称由 `,` 分割 |
| `autoexec.locations`                 | 指定脚本存放位置，支持多位置指定                             | 脚本位置                      |
| `autoexec.clean-disabled`            | 配置以下两个配置使用，为保护数据库不被某类脚本整理问题，导致校验失败直接清库。此处必须配置为 false | false                         |
| `autoexec.validate-on-migrate`       | 脚本集合在执行前是否先校验。                                 | true                          |
| `autoexec.clean-on-validation-error` | 脚本校验规则，出现不通过校验时处理策略。配置为 true，则会直接清库重新执行全量脚本。配置为 false，则直接抛错。开发生产环境都建议配置为 false | false                         |
| `autoexec.encoding`                  | 脚本读取时字符集指定，默认 `UTF-8`                           | 按需配置                      |
| `autoexec.baseline-on-migration`     | 是否设置脚本执行基线，如果是在项目建立初就决定使用 `autoexec-script` 此处可以配置为false，否则为 true。 | 按需配置                      |
| `autoexec.baseline-version`          | 脚本执行基线版本号，如果是在项目建立初就决定使用 `autoexec-script` 可不增加此配置，否则按项目配置正确。请跳转[脚本执行基线设定场景](#脚本执行基线设定) | 按需配置                      |
| `autoexec.out-of-order`              | 检查出脚本有漏执行情况，是否补充执行。必须配置为 true，方便开发过程中的补充脚本 | true                          |
| `autoexec.table`                     | 脚本执行记录表名设置，默认为 `flyway_schema_history`         | 按需配置                      |
| `autoexec.placeholders`              | SQL 脚本中变量替换，以 `Map` 结构进行配置。注意 SQL 任何满足变量替换要求字符串都会被替换或报错。请跳转[脚本变量替换](#SQL 脚本中变量替换) | 按需配置                      |

> :bulb: :bulb: :bulb:
>
> - 以上为常用配置， 全量配置关注 `org.flywaydb.core.internal.configuration.ConfigUtils`。
>
> - 配置文件默认名称 `conf/autoexec.conf`。