# CampusFlow UI 设计系统

更新：2026-11-04。Android 使用 dp 与 sp；主题跟随系统，保留真实 HTTP 仓库、权限、分页与版本冲突契约。

## 色彩

| 语义 | Light | Dark |
| --- | --- | --- |
| Primary | #0D6259 | #8BD5C5 |
| OnPrimary | #FFFFFF | #00382F |
| Background | #F5F7F5 | #101614 |
| Surface | #FFFFFF | #18211D |
| Elevated | #FFFFFF | #202C26 |
| Text | #18251F | #EDF3EF |
| SecondaryText | #46584E | #BDCCC2 |
| MutedText | #5C6C63 | #A4B5AA |
| Border | #DDE5DF | #34463B |
| ControlOutline | #718078 | #7E9385 |
| Success | #21613D | #91D6A5 |
| Warning | #805000 | #F2C66D |
| Error | #AF3038 | #FFB3B8 |

普通文字对比度门槛为 4.5:1；必要控件边界为 3:1。ColorContrastTest 验证文字及语义反馈颜色与基础背景组合，不代表已经覆盖屏幕上所有可能的叠色组合。细描边仅表达卡片层级，不用来承担输入框识别。

## 布局与排版

- 间距：4、8、16、24、32、48；页面边距 24，表单最大宽度 440。
- 圆角：标签 8、控件 16、卡片 20、主展示与弹层 24。
- 卡片：0.5dp 微描边与 1dp 阴影；深色主要通过表面亮度建立深度。
- 主按钮和输入框最小高度 56；辅助按钮与图标热区至少 48。
- Display：36/44 SemiBold；H1：28/36 SemiBold；Body Large：18/28 Medium；Body：16/24 Regular；Caption：12/18。
- 保留字体缩放；指标区域和管理入口按可用宽度调整排列。列表动态标题与摘要截断；错误说明可换行。

## 公共组件状态矩阵

| 组件 | Default | Focus / Active | Pressed / Loading | Error | Disabled |
| --- | --- | --- | --- | --- | --- |
| CampusTextInput | Surface、1dp Outline、Label | 聚焦使用 Material 2dp Primary 描边；填写后失焦保持正文 | 图标采用 Material 触控反馈 | Error 描边与原有字段错误 | 独立容器与可辨识文本，不接收编辑 |
| CampusPrimaryButton | Primary 实底、16dp 圆角 | Material 无障碍焦点 | Material 水波纹；业务请求锁定，登录显示局部进度 | 就近错误提示，保留重试 | Material 禁用状态 |
| CampusSecondaryButton | 中性表面、描边 | Material 焦点 | 水波纹 | 错误在关联内容显示 | 禁止触发 |
| CampusTonalButton | 中性浅容器、16dp 圆角 | Material 焦点 | 水波纹 | 错误在关联内容显示 | 禁止触发 |
| TextButton / IconButton | 无底色或轻容器 | Material 焦点 | 水波纹 | 保留内容区反馈 | 禁止触发 |
| 空间与管理卡片 | 中性表面、微描边和轻阴影 | 语义化标题 | 点击水波纹 | 页面局部错误与重试 | 根据业务权限移除操作 |

## 信息与状态

- 登录仅保留一个主标题；注册入口为“注册账号”，低频服务器地址折叠到“连接设置”。
- 找空间标题简化为“找空间”；移除管理工作台品牌宣传卡和评价页装饰口号。
- 空状态保留标题与行动按钮，移除重复解释；指标缺失仍显示未知，不填充演示读数。
- 原有加载骨架、下拉刷新、分页、错误重试、提交锁定、键盘适配、草稿保护和冲突重载保持。
- GCJ-02 标识、预计时段限制和不可逆业务约束保留，避免精简文案后损失操作依据。

## 验证记录

- assembleDebug、assembleDebugAndroidTest 通过。
- 25 项单元测试通过，包含 2 项颜色对比度测试。
- AuthFlowTest 3 项在普通尺寸通过；同样 3 项在深色、320dp 宽度、1.3 倍字体下通过。测试不创建数据库记录。
- 登录亮暗截图：app/build/validation/clean-light.png、clean-dark.png。
- 本轮未验收真实空间、评价和管理员登录后的有数据状态；这部分不能用演示数据代替端到端验收。
