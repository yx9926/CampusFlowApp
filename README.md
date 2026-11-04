# CampusFlow Android

全局 UI 规范与最新重构验证记录见 [UI_DESIGN_SYSTEM.md](UI_DESIGN_SYSTEM.md)。2026-11-04 起采用深松石绿主操作、中性表面、收敛圆角与统一描边表单，历史 Expressive 重构记录保留作背景。

Kotlin + Jetpack Compose + Material 3 客户端。所有业务仅访问实际 Spring Boot 后端，不包含演示登录、内存样例仓库、模拟断网或模拟设备控制。

## 本机后端与手机

默认后端根地址为 http://127.0.0.1:8080。该地址通过 USB 的 ADB reverse 指向开发电脑。手机连接电脑热点时，将连接地址改为电脑热点网关，例如 http://192.168.137.1:8080。仅调试构建允许 HTTP，发布部署使用可达的 HTTPS 服务器，可在登录页调整地址。

1. 在 CampusFlow_Last 项目执行 scripts/initialize-real-database.ps1 初始化实际 MySQL 数据库，不导入示例空间或读数。
2. 执行 scripts/start-backend-local.ps1 启动真实后端，并按终端提示设置首次管理员账号。
3. 手机启用 USB 调试并授权后执行：

    D:\develop\sdk\android\platform-tools\adb.exe -s 手机序列号 reverse tcp:8080 tcp:8080

4. 安装 app/build/outputs/apk/debug/app-debug.apk，在登录页使用真实账号。注册写入数据库，管理员账号由管理员分配。

USB 转发在拔线或重连后可能需要重建。脱离 USB 使用时，需要将登录页服务器地址改为手机可达的局域网地址或 HTTPS 域名。

Cookie 与 CSRF 由 HttpAuthRepository 管理，登录后刷新 CSRF；401 返回登录，403 显示权限错误。会话与密码不持久化，进程结束后重新登录。

## 功能与真实数据

用户端：登录、注册、找空间、筛选、详情、公开评价、我的评价、评价编辑和撤回、个人中心。

数据管理员：空间管理、真实设备发现与绑定、评价审核、真实硬件记录检查。

服务器管理员：实际服务及设备概况、账号管理、指标有效期与推荐配置、操作日志。概况不提供人工改变在线状态或生成读数的操作。

人数、噪声与进出事件来自实际硬件上报并写入数据库。未绑定、无记录、离线、过期或无效时显示未知，不回退到生成的数据。仅前台每 5 秒刷新，分页保留旧指标的数据年龄。没有实际空间时显示空状态。

搜索前通过高德地图点击位置、点击 POI 或搜索地点/地址的联想结果选择起点，确认后用于附近空间查询。地图首次使用需同意高德服务说明。获得前台位置权限后，系统定位与高德单次定位并行获取位置，统一使用 GCJ-02 并缩放到当前位置附近。定位失败或未授权时可继续搜索地点。

地图和搜索使用高德 Android SDK，包含 350ms 搜索防抖、超时与重试、空结果、地图加载失败提示、逆地理编码及生命周期释放。控制台需绑定包名 `com.campus` 与部署电脑实际使用的调试、发布签名 SHA1。2026-11-04 修正 Key 绑定后，真实地址搜索、地图渲染和模拟器定位选点测试通过。验收测试为 `OriginPickerTest`，不写数据库；模拟器需使用支持高德原生库的 ARM 兼容模式。

评价、审核、账号和配置使用 expectedVersion；硬件编辑使用 version。冲突保留草稿并要求显式重新加载。每个空间最多绑定一台设备，包括停用设备；有上传历史后不可换绑或解绑。空间接口当前没有版本控制，与 Web 契约一致。

## 高德 Key

Key 从被版本控制忽略的 local.properties 中的 amap.android.key 读取，并注入清单 com.amap.api.v2.apikey。支持通过 Gradle 属性或环境变量 AMAP_ANDROID_KEY 覆盖。包名为 com.campus，高德控制台需分别绑定实际调试和发布签名 SHA1。

## 构建与测试

    .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest

单元测试覆盖真实模式的参数校验、指标时效与来源、版本草稿、权限入口、实际设备发现/绑定选项约束。客户端不再通过内存演示库验证服务器端业务规则；这些规则需由后端集成测试和真实接口验收覆盖。

AuthFlowTest 的三个界面测试不写数据库，可独立执行。AdminFlowTest 和 CampusFlowTest 直接调用实际后端，不创建样例业务数据。需在测试设备 App 私有目录 files/backend-test.properties 配置 server、USER.username/password、DATA_ADMIN.username/password、SERVER_ADMIN.username/password。没有对应真实验收账号时明确跳过，不能将跳过统计为联调通过。测试文件包含秘密，不得提交。

浅深色主题延续 Web 青绿色 #137D72；保留加载、空数据、错误重试、分页、表单锁定、键盘与安全区适配。

## Material 3 Expressive 重构（2026-10-28）

使用 MaterialExpressiveTheme、expressive MotionScheme、LoadingIndicator 与按钮形状反馈。Material 3 固定为 1.5.0-alpha13，以匹配现有 Android 36.1 编译 SDK；最新 beta02 要求 Android 37，不纳入本次升级。

登录、找空间、详情、评价、个人中心和八个管理页面统一为大圆角、青绿与黄绿层次、简洁图标和轻量导航。管理工作台采用自适应双列入口，小屏和大字体改为单列；人数与噪声突出数值，次要字段可展开。服务器地址、操作说明与更新时间按需展开，减少常驻说明文字。

筛选、评价与管理编辑使用底部弹层；保留关闭草稿确认、请求锁定、版本冲突重载和异常重试。搜索起点置于筛选顶部，不恢复默认假坐标，不增加演示数据。真实 HTTP 仓库及后端接口保持原有契约。

验证：assembleDebug、assembleDebugAndroidTest、23 项单元测试与 3 项认证界面测试通过。ExpressiveUiTest 使用既有真实管理员，覆盖浅色、深色、320dp 小屏/1.3 倍字体、筛选弹层、概况读取和配置草稿保护，未向数据库保存测试修改。实际截图保存在 app/build/validation/expressive-*.png。详情与评价的重构已编译，但当前真实库无空间与评价，未对有数据状态进行端到端界面验收。
