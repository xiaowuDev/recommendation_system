import { computed, readonly, ref, watch } from 'vue'

export type AppLocale = 'en' | 'zh'

const STORAGE_KEY = 'datasource-ui-locale'

const messages: Record<AppLocale, Record<string, string>> = {
  en: {
    'locale.en': 'EN',
    'locale.zh': '中文',
    'brand.kicker': 'Curated Ops',
    'brand.name': 'Data Source Manager',
    'brand.meta': 'Local Control Surface',
    'nav.dashboard': 'Dashboard',
    'nav.connections': 'Connections',
    'nav.ingestion': 'Ingestion',

    'dashboard.eyebrow': 'Operations Overview',
    'dashboard.title': 'A calmer control room for every data source you operate.',
    'dashboard.description': 'Shape connection lifecycles, validate targets, and move into ingestion with a cleaner surface and less visual noise.',
    'dashboard.create': 'Create Connection',
    'dashboard.openIngestion': 'Open Ingestion',
    'dashboard.connectionsTitle': 'Connection Library',
    'dashboard.connectionsDesc': 'Every source profile is surfaced as a tactile card so browsing feels lighter and more deliberate.',
    'dashboard.tracked': '{count} tracked',
    'dashboard.loading': 'Curating connection cards...',
    'dashboard.metrics.connections.label': 'Connections',
    'dashboard.metrics.connections.one': 'Configured source',
    'dashboard.metrics.connections.other': 'Configured sources',
    'dashboard.metrics.types.label': 'Active Types',
    'dashboard.metrics.types.helper': 'Distinct source categories',
    'dashboard.metrics.ready.label': 'Ready For Ingestion',
    'dashboard.metrics.ready.helper': 'Network-based pipelines',
    'dashboard.metrics.newest.label': 'Newest Profile',
    'dashboard.metrics.newest.helper': 'Latest onboarding',
    'dashboard.noConnections': 'No connections yet',

    'connections.eyebrow': 'Connection Atelier',
    'connections.title': 'Build a source catalog that feels curated, not crowded.',
    'connections.description': 'Browse every profile from a focused sidebar, then move into creation or inspection without losing your sense of place.',
    'connections.new': 'New Connection',
    'connections.closeCreator': 'Close Creator',
    'connections.cancel': 'Cancel',
    'connections.sidebarTitle': 'Connection Library',
    'connections.sidebarAvailable': '{count} source profiles available',
    'connections.loading': 'Loading source profiles...',
    'connections.errorLoad': 'Failed to load connections',
    'connections.errorTypes': 'Failed to load supported types',
    'connections.emptyLibrary': 'No connections yet. Create the first profile to start shaping the library.',
    'connections.emptyWorkspaceKicker': 'Quiet Workspace',
    'connections.emptyWorkspaceTitle': 'Select a connection to inspect its masked config, tests, and ingestion actions.',
    'connections.emptyWorkspaceDesc': 'Or open the creator to add a new profile with a more polished configuration flow.',
    'connections.metric.kafka': 'Kafka',
    'connections.metric.mysql': 'MySQL',
    'connections.metric.redis': 'Redis',
    'connections.metric.excel': 'Excel',
    'connections.metric.helper': 'Profiles registered',

    'card.added': 'Added {date}',
    'card.defaultDesc': 'A refined source profile ready for validation and ingestion workflows.',
    'card.maskedReady': 'Masked config ready',
    'card.inspect': 'Inspect',
    'card.listEmpty': 'No connections yet. Create one to start building your source library.',

    'detail.eyebrow': 'Connection Detail',
    'detail.meta': 'Created {created} and last updated {updated}.',
    'detail.test': 'Test Connection',
    'detail.testing': 'Testing Connection...',
    'detail.maskedConfig': 'Masked Configuration',
    'detail.securePreview': 'Secure preview',
    'detail.validation': 'Validation Surface',
    'detail.immediateFeedback': 'Immediate feedback',
    'detail.runningChecks': 'Running connection checks...',
    'detail.idleValidation': 'Run a connection test to validate the current profile and capture an audit trail.',
    'detail.ingestionControls': 'Ingestion Controls',
    'detail.runOnDemand': 'Run on demand',
    'detail.validationHistory': 'Validation History',
    'detail.recentChecks': '{count} recent checks',

    'form.eyebrow': 'Create Source',
    'form.title': 'Compose a refined connection profile',
    'form.description': 'Keep the first layer descriptive, then tune the transport details underneath with just enough structure to stay fast.',
    'form.connectionName': 'Connection Name',
    'form.sourceType': 'Source Type',
    'form.descriptionLabel': 'Description',
    'form.descriptionPlaceholder': 'Optional notes, ownership context, or Pretext-enhanced description',
    'form.configTitle': 'Configuration',
    'form.settings': '{type} settings',
    'form.bootstrapServers': 'Bootstrap Servers',
    'form.topic': 'Topic',
    'form.clientId': 'Client ID',
    'form.jdbcUrl': 'JDBC URL',
    'form.username': 'Username',
    'form.password': 'Password',
    'form.host': 'Host',
    'form.port': 'Port',
    'form.database': 'Database',
    'form.keyPattern': 'Key Pattern',
    'form.excelNote': 'Excel connections stay lightweight. Attach the workbook later during a test or ingestion run.',
    'form.footer': 'Profiles are stored with masked config previews and can be tested immediately after creation.',
    'form.create': 'Create Connection',
    'form.creating': 'Creating Connection...',
    'form.errorRequiredName': 'Connection name is required',
    'form.errorCreateFailed': 'Failed to create connection',
    'form.placeholder.connectionName': 'e.g. analytics-kafka-primary',
    'form.placeholder.clientId': 'Optional client identity',
    'form.placeholder.dbUser': 'db user',
    'form.placeholder.secret': 'Optional secret',
    'form.placeholder.redisHost': 'redis host',
    'form.placeholder.hostPort': 'host:port',
    'form.placeholder.topic': 'topic name',
    'form.placeholder.jdbc': 'jdbc:mysql://cluster:3306/database',
    'form.placeholder.pattern': '*',
    'form.type.kafka.title': 'Streaming message fabric',
    'form.type.kafka.detail': 'Shape brokers, topic routing, and a client identity tuned for ingestion bursts.',
    'form.type.mysql.title': 'Structured relational feed',
    'form.type.mysql.detail': 'Define a JDBC profile with deliberate credentials and query-ready access.',
    'form.type.redis.title': 'Keyspace driven lookup',
    'form.type.redis.detail': 'Target a host, database, and key pattern for lightweight extraction flows.',
    'form.type.excel.title': 'File-based onboarding',
    'form.type.excel.detail': 'Keep the profile minimal and attach spreadsheets only when an ingestion run begins.',

    'ingestion.eyebrow': 'Ingestion Studio',
    'ingestion.title': 'Launch ingestion runs from a quieter, more tactile workflow.',
    'ingestion.description': 'Switch between saved network connections and spreadsheet imports without losing the operational context of each run.',
    'ingestion.tab.connections': 'Connections',
    'ingestion.tab.excel': 'Excel Upload',
    'ingestion.spreadsheetTitle': 'Spreadsheet Ingestion',
    'ingestion.spreadsheetDesc': 'Upload an `.xlsx` file, tune parsing options, and inspect the sample rows immediately.',
    'ingestion.fileFlow': 'File-based flow',
    'ingestion.connectionRunsTitle': 'Connection Driven Runs',
    'ingestion.connectionRunsDesc': 'Pick a saved profile, configure run parameters, and review recent ingestion history in one surface.',
    'ingestion.available': '{count} available',
    'ingestion.selectConnection': 'Select Connection',
    'ingestion.chooseConnection': 'Choose a connection',
    'ingestion.triggerRun': 'Trigger Run',
    'ingestion.tuneExecution': 'Tune execution inputs for {name}.',
    'ingestion.recentJobs': 'Recent Jobs',
    'ingestion.runs': '{count} runs',
    'ingestion.empty': 'Select a saved connection to unlock parameterized ingestion runs and recent execution history.',

    'trigger.maxRecords': 'Max Records',
    'trigger.timeout': 'Timeout (ms)',
    'trigger.sqlQuery': 'SQL Query',
    'trigger.maxRows': 'Max Rows',
    'trigger.keyPattern': 'Key Pattern',
    'trigger.scanCount': 'Scan Count',
    'trigger.maxKeys': 'Max Keys',
    'trigger.launching': 'Launching Run...',
    'trigger.button': 'Trigger Ingestion',
    'trigger.actionCopy': 'Execution requests run immediately against the selected profile.',
    'trigger.sqlPlaceholder': 'SELECT * FROM table LIMIT 100',
    'trigger.errorRun': 'Ingestion failed',

    'result.records': '{count} records',
    'jobs.empty': 'No ingestion jobs yet.',
    'jobs.errorLoad': 'Failed to load jobs',

    'excel.title': 'Upload workbook',
    'excel.description': 'Choose a spreadsheet and tune parsing options before the ingestion run begins.',
    'excel.fileOnly': '.xlsx only',
    'excel.selectFile': 'Select .xlsx file',
    'excel.sheetName': 'Sheet Name (optional)',
    'excel.firstSheet': 'First sheet',
    'excel.headerRow': 'Header Row',
    'excel.maxRows': 'Max Rows',
    'excel.processing': 'Processing Workbook...',
    'excel.import': 'Import Excel',
    'excel.noFile': 'No file selected yet',
    'excel.errorRun': 'Excel ingestion failed',

    'detail.errorTest': 'Test failed',

    'status.SUCCESS': 'Success',
    'status.COMPLETED': 'Completed',
    'status.FAILED': 'Failed',
    'status.BLOCKED': 'Blocked',
    'status.RUNNING': 'Running',
    'status.PENDING': 'Pending',
    'status.INFO': 'Info',

    'table.empty': 'No data',
    'common.available': '{count} available'
  },
  zh: {
    'locale.en': 'EN',
    'locale.zh': '中文',
    'brand.kicker': '精选运维',
    'brand.name': '数据源管理器',
    'brand.meta': '本地控制台',
    'nav.dashboard': '仪表盘',
    'nav.connections': '连接管理',
    'nav.ingestion': '导入',

    'dashboard.eyebrow': '运行概览',
    'dashboard.title': '为每一个数据源准备一个更安静、更清晰的控制室。',
    'dashboard.description': '在更干净的界面里管理连接生命周期、验证目标地址，并进入导入流程。',
    'dashboard.create': '新建连接',
    'dashboard.openIngestion': '打开导入',
    'dashboard.connectionsTitle': '连接库',
    'dashboard.connectionsDesc': '每个数据源配置都会以更有质感的卡片呈现，让浏览和筛选更轻松。',
    'dashboard.tracked': '已跟踪 {count} 个',
    'dashboard.loading': '正在整理连接卡片...',
    'dashboard.metrics.connections.label': '连接数',
    'dashboard.metrics.connections.one': '已配置连接',
    'dashboard.metrics.connections.other': '已配置连接',
    'dashboard.metrics.types.label': '数据源类型',
    'dashboard.metrics.types.helper': '不同类别数量',
    'dashboard.metrics.ready.label': '可执行导入',
    'dashboard.metrics.ready.helper': '网络型数据源',
    'dashboard.metrics.newest.label': '最新配置',
    'dashboard.metrics.newest.helper': '最近创建',
    'dashboard.noConnections': '暂无连接',

    'connections.eyebrow': '连接工作台',
    'connections.title': '把数据源目录做得更有秩序，而不是越堆越乱。',
    'connections.description': '在专注的侧边栏中浏览每个连接配置，并在创建和查看之间流畅切换。',
    'connections.new': '新建连接',
    'connections.closeCreator': '关闭创建器',
    'connections.cancel': '取消',
    'connections.sidebarTitle': '连接库',
    'connections.sidebarAvailable': '共 {count} 个连接配置',
    'connections.loading': '正在加载连接配置...',
    'connections.errorLoad': '加载连接失败',
    'connections.errorTypes': '加载支持类型失败',
    'connections.emptyLibrary': '还没有任何连接。先创建第一个配置，开始搭建你的连接库。',
    'connections.emptyWorkspaceKicker': '空白工作区',
    'connections.emptyWorkspaceTitle': '选择一个连接，查看脱敏配置、测试结果和导入操作。',
    'connections.emptyWorkspaceDesc': '或者打开创建器，新增一个更精致的连接配置。',
    'connections.metric.kafka': 'Kafka',
    'connections.metric.mysql': 'MySQL',
    'connections.metric.redis': 'Redis',
    'connections.metric.excel': 'Excel',
    'connections.metric.helper': '已登记配置',

    'card.added': '创建于 {date}',
    'card.defaultDesc': '一个已经准备好做验证和导入的数据源配置。',
    'card.maskedReady': '脱敏配置已就绪',
    'card.inspect': '查看',
    'card.listEmpty': '还没有任何连接。先创建一个，开始建立你的数据源连接库。',

    'detail.eyebrow': '连接详情',
    'detail.meta': '创建于 {created}，最近更新于 {updated}。',
    'detail.test': '测试连接',
    'detail.testing': '测试连接中...',
    'detail.maskedConfig': '脱敏配置',
    'detail.securePreview': '安全预览',
    'detail.validation': '验证面板',
    'detail.immediateFeedback': '即时反馈',
    'detail.runningChecks': '正在执行连接检查...',
    'detail.idleValidation': '执行一次连接测试，验证当前配置并留下审计记录。',
    'detail.ingestionControls': '导入控制',
    'detail.runOnDemand': '按需执行',
    'detail.validationHistory': '验证历史',
    'detail.recentChecks': '最近 {count} 次检查',

    'form.eyebrow': '创建数据源',
    'form.title': '构建一个更精致的连接配置',
    'form.description': '先描述连接本身，再补充传输层配置，让填写过程既完整又足够高效。',
    'form.connectionName': '连接名称',
    'form.sourceType': '数据源类型',
    'form.descriptionLabel': '描述',
    'form.descriptionPlaceholder': '可填写备注、归属信息，或支持 Pretext 的描述',
    'form.configTitle': '连接配置',
    'form.settings': '{type} 配置',
    'form.bootstrapServers': 'Bootstrap Servers',
    'form.topic': '主题 Topic',
    'form.clientId': '客户端 ID',
    'form.jdbcUrl': 'JDBC 地址',
    'form.username': '用户名',
    'form.password': '密码',
    'form.host': '主机',
    'form.port': '端口',
    'form.database': '数据库',
    'form.keyPattern': 'Key 模式',
    'form.excelNote': 'Excel 类型保持轻量即可，文件在测试或导入时再上传。',
    'form.footer': '配置保存后会展示脱敏预览，并且可以立刻发起连接测试。',
    'form.create': '创建连接',
    'form.creating': '创建连接中...',
    'form.errorRequiredName': '连接名称不能为空',
    'form.errorCreateFailed': '创建连接失败',
    'form.placeholder.connectionName': '例如 analytics-kafka-primary',
    'form.placeholder.clientId': '可选的客户端标识',
    'form.placeholder.dbUser': '数据库用户名',
    'form.placeholder.secret': '可选密码',
    'form.placeholder.redisHost': 'redis 主机地址',
    'form.placeholder.hostPort': 'host:port',
    'form.placeholder.topic': 'topic 名称',
    'form.placeholder.jdbc': 'jdbc:mysql://cluster:3306/database',
    'form.placeholder.pattern': '*',
    'form.type.kafka.title': '消息流接入',
    'form.type.kafka.detail': '配置 broker、topic 路由以及适合导入任务的客户端身份。',
    'form.type.mysql.title': '结构化关系型数据',
    'form.type.mysql.detail': '定义 JDBC 连接信息、凭证以及后续查询所需的访问方式。',
    'form.type.redis.title': 'Key 空间扫描',
    'form.type.redis.detail': '指定主机、数据库和 Key 模式，用于轻量提取流程。',
    'form.type.excel.title': '文件型接入',
    'form.type.excel.detail': '保持配置简单，把实际文件上传动作放到导入时再处理。',

    'ingestion.eyebrow': '导入工作台',
    'ingestion.title': '用更安静、更有质感的流程发起导入任务。',
    'ingestion.description': '在已保存连接和 Excel 上传两种模式之间切换，同时保留每次执行的上下文。',
    'ingestion.tab.connections': '连接模式',
    'ingestion.tab.excel': 'Excel 上传',
    'ingestion.spreadsheetTitle': '表格导入',
    'ingestion.spreadsheetDesc': '上传 `.xlsx` 文件，调整解析参数，并立即查看样例数据。',
    'ingestion.fileFlow': '文件导入',
    'ingestion.connectionRunsTitle': '连接驱动导入',
    'ingestion.connectionRunsDesc': '选择已有连接，配置执行参数，并在同一界面查看最近任务记录。',
    'ingestion.available': '可用 {count} 个',
    'ingestion.selectConnection': '选择连接',
    'ingestion.chooseConnection': '请选择一个连接',
    'ingestion.triggerRun': '触发执行',
    'ingestion.tuneExecution': '为 {name} 配置本次执行参数。',
    'ingestion.recentJobs': '最近任务',
    'ingestion.runs': '{count} 次任务',
    'ingestion.empty': '请选择一个已保存连接，解锁参数化导入和最近执行记录。',

    'trigger.maxRecords': '最大记录数',
    'trigger.timeout': '超时（毫秒）',
    'trigger.sqlQuery': 'SQL 查询',
    'trigger.maxRows': '最大行数',
    'trigger.keyPattern': 'Key 模式',
    'trigger.scanCount': '扫描批次',
    'trigger.maxKeys': '最大 Key 数',
    'trigger.launching': '正在发起执行...',
    'trigger.button': '触发导入',
    'trigger.actionCopy': '执行请求会立即针对当前连接配置发起导入。',
    'trigger.sqlPlaceholder': 'SELECT * FROM table LIMIT 100',
    'trigger.errorRun': '导入失败',

    'result.records': '{count} 条记录',
    'jobs.empty': '还没有导入任务。',
    'jobs.errorLoad': '加载任务失败',

    'excel.title': '上传工作簿',
    'excel.description': '选择一个表格文件，并在导入前调整解析参数。',
    'excel.fileOnly': '仅支持 .xlsx',
    'excel.selectFile': '选择 .xlsx 文件',
    'excel.sheetName': 'Sheet 名称（可选）',
    'excel.firstSheet': '默认首个 sheet',
    'excel.headerRow': '表头行',
    'excel.maxRows': '最大行数',
    'excel.processing': '正在处理工作簿...',
    'excel.import': '导入 Excel',
    'excel.noFile': '尚未选择文件',
    'excel.errorRun': 'Excel 导入失败',

    'detail.errorTest': '连接测试失败',

    'status.SUCCESS': '成功',
    'status.COMPLETED': '已完成',
    'status.FAILED': '失败',
    'status.BLOCKED': '阻塞',
    'status.RUNNING': '运行中',
    'status.PENDING': '等待中',
    'status.INFO': '信息',

    'table.empty': '暂无数据',
    'common.available': '可用 {count} 个'
  }
}

function resolveInitialLocale(): AppLocale {
  if (typeof window === 'undefined') {
    return 'en'
  }

  const stored = window.localStorage.getItem(STORAGE_KEY)
  if (stored === 'en' || stored === 'zh') {
    return stored
  }

  return window.navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en'
}

const locale = ref<AppLocale>(resolveInitialLocale())

function interpolate(template: string, params?: Record<string, string | number>): string {
  if (!params) {
    return template
  }

  return Object.entries(params).reduce(
    (result, [key, value]) => result.replaceAll(`{${key}}`, String(value)),
    template
  )
}

function translate(key: string, params?: Record<string, string | number>): string {
  const fallback = messages.en[key] ?? key
  const template = messages[locale.value][key] ?? fallback
  return interpolate(template, params)
}

function setLocale(next: AppLocale) {
  locale.value = next
}

function toggleLocale() {
  locale.value = locale.value === 'en' ? 'zh' : 'en'
}

watch(locale, value => {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(STORAGE_KEY, value)
  }
  if (typeof document !== 'undefined') {
    document.documentElement.lang = value === 'zh' ? 'zh-CN' : 'en'
    document.documentElement.setAttribute('data-locale', value)
  }
}, { immediate: true })

export function useLocale() {
  return {
    locale: readonly(locale),
    localeCode: computed(() => locale.value === 'zh' ? 'zh-CN' : 'en-US'),
    isZh: computed(() => locale.value === 'zh'),
    setLocale,
    toggleLocale,
    t: translate
  }
}
