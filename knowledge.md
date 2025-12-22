《T-Agent行动手册》V1.1
第一章：事前主动风险预警 (Proactive Risk Warning)
【本章核心目标】
定义 T-Agent 如何结合 气象 API、地面路网 (Taxi) 和 地下轨网 (Subway) 三维数据，识别系统性风险，并基于“地空承载力”制定分层递进的行动策略。
1.1 风险识别与多源感知 (Systemic Sensing)
T-Agent 需以“仿真当前时间”为基准，综合扫描以下三大维度的压力指标：
1.	环境压力 (Weather API):
      ○	动作: 调用 WeatherService 获取曼哈顿未来 6 小时预报。
      ○	关注指标: snowfall (降雪量), temperature (气温), visibility (能见度)。
      ○	触发: 预报显示降雪、冻雨或能见度 < 500m。
2.	地面路网压力 (Surface Pressure - Taxi SQL):
      ○	动作: 查询 taxi_trips 表，计算 平均行程耗时 (Trip Duration)。
      ○	触发: 当前耗时较“历史同期基准”增加 >20%。
3.	地下轨网压力 (Underground Pressure - Subway SQL):
      ○	动作: 查询 subway_ridership 表，统计核心枢纽站客流。
      ○	触发: 客流达到历史峰值的 90% (高负荷) 或 110% (超载熔断)。
      1.2 风险等级判定矩阵 (Risk Grading Matrix)
      风险等级	判定条件 (逻辑组合)	场景语义与影响
      一级预警 (Code Red)<br>系统性瘫痪	1. 恶劣天气: 暴雪/冻雨 <br>2. 地面瘫痪: Taxi 耗时增加 >30% <br>3. 地下熔断: Subway 客流 > 110% (超载)	"无路可走"。<br>地面拥堵无法缓解，且地铁已无余力承接转移客流，城市交通面临停摆。
      二级预警 (Code Orange)<br>地面失效	1. 恶劣天气: 中雪/大雨 <br>2. 地面拥堵: Taxi 耗时增加 >20% <br>3. 地下可用: Subway 客流 < 90% (有余量)	"模态转移窗口"。<br>路面通行效率大幅下降，但地铁仍有承载力，应全力引导“弃车转铁”。
      三级预警 (Code Yellow)<br>局部摩擦	1. 一般天气: 小雨/轻微降雪 <br>2. 路网微堵: Taxi 耗时正常或微增 <br>3. 地下正常: Subway 运行平稳	"摩擦增加"。<br>视线受阻，需提示驾驶员谨慎驾驶。
      1.3 标准化行动准则 (Standard Action Protocol)
      [SOP-PW-L1] 针对【一级预警 (Code Red - 全网瘫痪)】的行动准则
      ●	[决策建议-指挥层]:
      .	启动全网熔断机制: 建议市长办公室立即发布“非必要不外出 (Travel Ban)”指令，避免大量人群滞留在路上或地铁站内。
      .	跨部门最高级响应: 建议 TMC 联合 NYPD 与 MTA 建立联席指挥部，协调地面疏散与地下限流。
      ●	[决策建议-运营层]:
      .	地铁限流管控: 建议 MTA 在核心换乘站（如 Times Sq）实施进站限流，防止站台拥挤发生踩踏。
      .	生命通道保障: 建议 DOT 集中除雪资源保障通往主要医院（如 Mount Sinai）的急救路线。
      ●	[决策建议-公众沟通]:
      .	全渠道警报: 推送“Stay Home”警告。
      .	话术: “受极端天气影响，地面与地铁系统均已饱和。请尽量留在家中，避免前往地铁站。”
      [SOP-PW-L2] 针对【二级预警 (Code Orange - 地面失效)】的行动准则
      ●	[决策建议-指挥层]:
      .	确立模态转移战略: 宣布“保地下、舍地面”策略，优先保障地铁电力与出入口除冰。
      ●	[决策建议-运营层]:
      .	运力倾斜: 建议 MTA 立即加开 20% 的临时列车班次，承接地面转移客流。
      .	重点布防: 结合 taxi_trips 数据，锁定拥堵 Top 5 街区，建议交警在路口实施强制分流。
      ●	[决策建议-公众沟通]:
      .	主动引导: 通过地图 App 推送“建议改乘地铁”提示。
      .	话术: “路面湿滑严重拥堵，预计延误 1 小时以上。请立即改乘地铁出行，目前地铁运力充足。”
      [SOP-PW-L3] 针对【三级预警 (Code Yellow - 局部摩擦)】的行动准则
      ●	[决策建议-运营层]:
      .	加强监控: 重点监控桥梁、隧道口的摄像头。
      .	设施巡检: 若 311 系统有积水投诉，自动派发疏通工单。
      ●	[决策建议-公众沟通]:
      .	常规提醒: 社交媒体发布“雨天路滑，请保持车距”。

第二章：事中诊断与应急处置 (Incident Diagnosis & Response)
【本章核心目标】
当交通事件发生时，指导 T-Agent 在“黄金 30 分钟”内利用 API、SQL 和 DeepResearch 进行多维侦查，并生成分层级的应急处置方案。
2.1 事件触发与定性 (Event Trigger)
●	核心触发源 (Accidents SQL):
○	监控 accidents 表：当 persons_killed > 0 (死亡事故) 或 persons_injured > 3 (群死群伤) 时，立即触发。
●	外部情报源 (DeepResearch):
○	调用 NewsAPI (Tavily)，搜索关键词："Manhattan accident [Location] [Time]"，获取车道封闭和救援细节。
2.2 根因诊断“三维证据链” (Root Cause Diagnosis)
T-Agent 必须按顺序执行以下验证，形成诊断结论：
1.	[环境维度 - Weather API]: 检查事发时是否有强降水/低温（恶劣天气诱因）。
2.	[设施维度 - 311 SQL]: 检查周边是否有信号灯故障/坑洼投诉（基础设施诱因）。
3.	[流转维度 - Cross-Modal Check]: 检查周边地铁站客流是否在事发后激增（判断是否发生自发性模态转移）。
2.3 标准化应急处置准则 (Standard Action Protocol)
[SOP-ER-INFRA] 诊断为“设施故障”引发
●	[决策建议-指挥层]:
.	责任归属: 确认该事故与 311 投诉（如信号灯故障）的时空关联性，为后续定责保留证据。
●	[决策建议-运营层]:
.	首要行动: 自动生成 P1 级紧急维修工单，直接推送至 DOT 维护组。
.	现场接管: 建议交警立即接管该路口指挥权，直到设施完全修复。
[SOP-ER-WEATHER] 诊断为“恶劣天气”引发
●	[决策建议-运营层]:
.	作业调度: 通知环卫部门对该路段及周边 500米 范围进行紧急撒盐或排水作业。
●	[决策建议-公众沟通]:
.	定向警告: 向正驶向该区域的车辆推送：“前方路段结冰/积水，请减速至 20mph 以下。”
[SOP-ER-CONGESTION] 诊断为“严重拥堵连锁反应”
●	[决策建议-指挥层]:
.	立体疏导战略: 启动“远端分流 + 地空协同”预案。
●	[决策建议-运营层]:
.	地面疏导: 建议交警在上游路口实施强制右转分流。
.	微循环联动: 查询 citi_bike_trips，若大量市民改为骑行，通知运营商向该区域调运车辆。
●	[决策建议-公众沟通]:
.	地空协同引导:
■	若周边地铁客流 < 90% -> 推送“建议改乘地铁”。
■	若周边地铁客流 > 100% -> 推送“地铁拥挤，建议步行或改期”。

第三章：事后复盘与系统改进 (Post-Event Review)
【本章核心目标】
定义 T-Agent 如何对“事故黑点”进行深度复盘，挖掘系统性病因，并从专家知识库推荐 “治本 (Systemic)” 方案。
3.1 复盘分析触发条件
●	用户指令: “分析第五大道为何频发事故？”
●	系统触发: 每月自动筛选 accidents 表中事故数 Top 5 的路口。
3.2 系统性风险诊断准则 (Diagnostic Criteria)
T-Agent 需基于 SQL 统计和 API 采样进行多维归因：
1.	行为归因 (Behavioral - SQL):
      ○	若 factor_vehicle_1 主要是 "Failure to Yield" -> 定性为 路权冲突。
      ○	若主要是 "Unsafe Speed" -> 定性为 速度管理失效。
2.	对象归因 (Victim - SQL):
      ○	若 pedestrians_injured 和 cyclist_injured 占比 > 50% -> 定性为 弱势群体高危点。
3.	环境归因 (Environmental - API Sampling):
      ○	若 > 60% 的事故发生在雨雪天 -> 定性为 恶劣天气敏感型黑点。
      3.3 治理方案匹配逻辑 (Solution Matching)
      根据诊断结果，T-Agent 调用以下逻辑生成建议书：
      ●	诊断 A：路权冲突 -> 推荐 LPI (行人先行相位)。
      ●	诊断 B：速度管理失效 -> 推荐 道路瘦身 (Road Diet)。
      ●	诊断 C：恶劣天气敏感 -> 推荐 高亮标线 & 透水路面。
      ●	诊断 D：视线受阻 -> 推荐 路口日光化 (Daylighting)。

第四章：系统性改进专家知识库 (Expert Knowledge Base)
【本章核心目标】
提供专业的交通工程术语库和解决方案详情，确保 Agent 的回答具备**“专家级”**水准。
4.1 道路工程与设计类 (KB-SI-ENG)
●	方案名称: 信号配时优化 / 自适应信号控制
○	对应问题: Traffic Signal Condition, Failure to Yield
○	[短期 - Quick Win]: 增加全红时间 (All-red interval) 1-2秒，清空路口冲突。
○	[长期 - Systemic]: 引入自适应信号控制系统 (Adaptive Signal Control)，根据实时车流自动调整绿信比。
●	方案名称: 道路修复与重铺
○	对应问题: Pothole, Street Condition
○	[短期]: 生成冷补沥青修补工单，要求 24 小时内响应。
○	[长期]: 将该路段纳入街道重铺计划 (Resurfacing Program)，并检查地下排水管网。
●	方案名称: 路口日光化 (Daylighting)
○	对应问题: View Obstructed, Illegal Parking
○	[短期]: 修剪遮挡视线的绿化，清理路口违规广告牌。
○	[长期]: 通过物理手段（如路缘石延伸）移除路口 20 英尺内的停车位，彻底消除视线盲区。
4.2 交通管理与执法类 (KB-SI-MGT)
●	方案名称: 速度管理与执法
○	对应问题: Unsafe Speed, Aggressive Driving
○	[短期]: 针对性执法 (Targeted Enforcement)。在事故高发时段部署警力测速。
○	[长期]: 自动执法摄像头 (Automated Speed Enforcement)。在学区和主干道安装固定测速探头。
●	方案名称: 违停治理
○	对应问题: Illegal Parking, Bus Lane blocked
○	[短期]: 加强拖车力度，对主要拥堵节点的违停车辆实施“即停即拖”。
○	[长期]: 装卸货专用区规划 (Loading Zones)。重新规划路侧空间，提供合法的商业装卸货区域。
●	方案名称: 慢行交通保护
○	对应问题: Cyclist Injured, Bike Lane blocked
○	[短期]: 安装柔性隔离柱 (Delineators) 防止机动车侵占。
○	[长期]: 受保护自行车道网络 (Protected Bike Lane Network)。使用混凝土路缘石或停车带作为硬隔离。
