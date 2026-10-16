# IDEA 配置与运行流程：首次打开 Java 项目和以后启动的操作说明

适用于当前 `base-mybatis-plus-service`。菜单名称按这次使用的中文版 IDEA 整理，其他版本可能显示为「导入」「Runner」等名称。

## 一、先知道要配什么

| 配置项 | 负责什么 | 当前项目选择 |
| --- | --- | --- |
| Maven 用户设置文件 | 从哪个仓库下载依赖 | 项目的 `.mvn/settings.xml` |
| Maven 导入程序的 JDK | 导入项目、解析 Maven 依赖时使用的 Java | `temurin-21` |
| Maven 运行程序的 JRE | 执行 Maven 构建、测试时使用的 Java | `temurin-21` |
| 项目 SDK | IDEA 编译和默认运行时使用的 Java | `temurin-21` |

本机 JDK 21 已安装在 `D:\DevTools\jdk-21.0.12.1+1`，可以直接添加到 IDEA。换电脑时需要先安装项目要求的 JDK，再选择那台电脑的安装目录。

## 二、首次打开项目

1. 点击左上角 **☰ → 文件 → 打开**。
2. 选择整个项目目录：`F:\公司项目\gitee\base-mybatis-plus-service`。
3. 如果提示按 Maven 项目打开或加载，选择加载。打开的是包含根 `pom.xml` 的目录，不是单独打开 `base-common`。

## 三、设置 Maven 下载仓库

1. 点击 **☰ → 文件 → 设置**，快捷键不生效时直接用菜单。
2. 左侧展开 **构建、执行、部署 → 构建工具 → Maven**。
3. **Maven 主路径** 保持「已捆绑（Maven 3）」，本次版本为 3.9.16。
4. 找到 **用户设置文件**，勾选这一行右侧的 **覆盖**。
5. 将输入框中的路径改成：

   ```text
   F:\公司项目\gitee\base-mybatis-plus-service\.mvn\settings.xml
   ```

6. **本地仓库** 保持自动读取即可。本次显示为 `C:\Users\12484\.m2\repository`，这里存放下载的依赖，可以供多个项目共用。
7. 点击 **应用**，先不关闭设置窗口。

`pom.xml` 决定下载哪些依赖，`settings.xml` 决定去哪里下载。当前项目使用公共仓库，公司项目可能需要自己的私服配置，不能直接照搬。

## 四、设置 Maven 导入和构建使用的 JDK

1. 点击左侧 **Maven 前面的小箭头**，展开子页面。
2. 点击 **正在导入**，找到 **导入程序的 JDK**。
3. 选择 `temurin-21`，确认路径为 `D:\DevTools\jdk-21.0.12.1+1`。
4. 点击左侧 **运行程序**。
5. 找到 **JRE**，同样选择 `temurin-21`。
6. 点击 **确定** 保存设置。

如果下拉框里没有 21，先按下一节添加 SDK，再返回这两处选择。

## 五、设置项目 SDK

1. 点击 **☰ → 文件 → 项目结构**。「项目结构」在「设置」下面。
2. 如果尚未添加 JDK 21，点击左侧 **平台设置 → SDK**。
3. 点击 SDK 列表上方的 **＋ → 添加 JDK**，选择目录 `D:\DevTools\jdk-21.0.12.1+1`。
4. 点击左侧上方 **项目设置 → 项目**。
5. 将 **SDK** 选择为 `temurin-21`。
6. 将 **语言级别** 选择为 **SDK 默认值**。
7. 点击 **确定**。

注意：在「SDK」页面添加了 JDK，只表示 IDEA 认识这个安装目录；还要在「项目」页面选择它，当前项目才会使用它。

## 六、重新同步依赖

1. 打开右侧 **Maven** 面板。找不到时，点击 **视图 → 工具窗口 → Maven**。
2. 点击面板上方的 **重新加载所有 Maven 项目** 按钮，鼠标停在图标上可查看名称。
3. 等 Maven 同步完成。如果有旧的同步任务一直运行，先取消旧任务，再重新加载。
4. 首次使用会下载依赖；已有缓存时会复用，不需要执行 `npm install`。

同步解析依赖不等于服务已经启动。下一节才是启动后端。

## 七、启动后端应用

1. 在左侧项目目录依次展开：

   ```text
   system-service
     → src
       → main
         → java
           → com.base.system
             → SystemApplication.java
   ```

2. 打开 `SystemApplication.java`。
3. 在右上角运行配置选择 **SystemApplication-local** 并运行；这个运行项会显式加载现有本机数据库和密钥配置。若从类旁绿色三角形新建运行项，须先在程序参数中补上后文的本机配置加载参数。
4. 等控制台出现 `Started SystemApplication`。默认端口是 **8080**。
5. 浏览器打开：

   ```text
   http://localhost:8080/health
   ```

6. 返回包含 `code: 0`、`service: "system-service"` 和 `status: "UP"` 的 JSON，说明后端应用可以响应。
7. 当前只启动这一个应用，后续亲友业务也放在 system 的 `modules/relations/` 中。

如果运行配置之前固定选择了 JDK 8，在 **运行 → 编辑配置** 中选择 `SystemApplication-local`，把 JRE 改为项目 SDK 或 `temurin-21`。

运行前准备：system 需要 PostgreSQL、数据库参数、RSA 私钥及公钥。生成密钥、管理员初始化及本机配置见 [基础规范与运行说明](基础规范与运行说明.md)。本机连接配置沿用现有 system 文件，密钥推荐使用绝对文件路径。

使用本机文件时，复制 system 的 `application-local.example.yml`。`application-local.yml` 不会打进 JAR，需在程序参数中填写 `--spring.profiles.active=local`，并通过 `--spring.config.additional-location=file:本机文件的绝对路径` 显式加载；完整例子见运行说明。

父工程、`base-common` 和 `base-security` 不提供运行入口。旧的 `RelationsApplication-local` 运行项已移除，`JWT_SECRET` 已不使用。

## 八、以后每天启动

已经配置好的同一项目，通常只需要：

1. 打开项目。
2. 运行 `SystemApplication-local`。
3. 打开 `http://localhost:8080/health` 检查响应。
4. 用完点击运行窗口中的红色停止按钮。

拉取代码后，如果 `pom.xml` 有变化，再重新加载 Maven。通常不用每次重新选 JDK 或重新下载全部依赖。

## 九、换项目时怎么判断配置

| 要判断的内容 | 看哪里 | 怎么选 |
| --- | --- | --- |
| 项目要求哪个 JDK | README、根 `pom.xml` 的 `java.version` 或编译器配置 | 按项目要求选择；当前项目是 21 |
| Maven 版本 | README、Maven Wrapper 或项目约定 | 当前项目可用 IDEA 捆绑的 Maven 3.9.16 |
| 仓库设置文件 | README、项目提供的 `settings.xml` 或团队说明 | 当前项目用 `.mvn/settings.xml`；公司 PMS 用原来的公司配置 |

打开当前项目的设置窗口，再配置该项目。不要用「新建项目设置」去修改所有新项目的默认值，也不用修改系统默认 JDK；本机可以同时保留 JDK 8 和 JDK 21。

## 十、常见情况

| 现象 | 先检查什么 |
| --- | --- |
| 一直显示「正在解析 Maven 依赖」 | 查看 Maven 同步输出；确认用户设置文件路径，按具体下载错误排查 |
| 提示 Java 版本不支持或无法编译 Java 21 | 检查项目 SDK、Maven 导入 JDK、Maven 运行 JRE 是否都为 21 |
| 找不到绿色运行按钮 | 确认 Maven 导入完成，打开对应服务的 Application 类 |
| 提示端口被占用 | 默认 8080；先检查是否已运行过，也可用 `--server.port=...` 更换端口 |
| Maven 同步成功，但浏览器打不开接口 | 运行对应 Application 并查看启动日志；同步成功只说明项目导入完成 |
| 提示找不到 PEM 密钥文件 | 检查运行配置中的 JWT_PUBLIC_KEY，system 还需 JWT_PRIVATE_KEY；优先填绝对文件路径 |

## 十一、可选：PowerShell 构建和运行

IDEA 已能运行时，不必额外执行本节。需要用终端验证时，在 PowerShell 中执行：

```powershell
Set-Location 'F:\公司项目\gitee\base-mybatis-plus-service'
$env:JAVA_HOME = 'D:\DevTools\jdk-21.0.12.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn.cmd -s .mvn/settings.xml clean verify
```

看到 `BUILD SUCCESS` 后，再执行：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\system-service\target\system-service-0.0.1-SNAPSHOT-exec.jar
```

数据库和密钥参数见运行说明；使用本机文件时在 JAR 后加上 `--spring.profiles.active=local --spring.config.additional-location=file:F:/公司项目/gitee/base-mybatis-plus-service/system-service/src/main/resources/application-local.yml`。只访问 8080 的 `/health`。终端运行时使用 `Ctrl+C` 停止；环境变量只影响当前终端，不改变系统默认 JDK。
