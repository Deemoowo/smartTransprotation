// 测试 Markdown 转换功能
function formatMessage(content) {
    if (!content) return '';

    // 先处理标题（在转义HTML之前，保持原始换行符）
    let formatted = content;

    // 处理标题 # Header (支持有空格和无空格的情况)
    formatted = formatted.replace(/^######\s*(.*$)/gm, '<h6>$1</h6>');
    formatted = formatted.replace(/^#####\s*(.*$)/gm, '<h5>$1</h5>');
    formatted = formatted.replace(/^####\s*(.*$)/gm, '<h4>$1</h4>');
    formatted = formatted.replace(/^###\s*(.*$)/gm, '<h3>$1</h3>');
    formatted = formatted.replace(/^##\s*(.*$)/gm, '<h2>$1</h2>');
    formatted = formatted.replace(/^#\s*(.*$)/gm, '<h1>$1</h1>');

    // 转义HTML特殊字符（但保留已生成的HTML标签）
    formatted = formatted.replace(/&/g, '&amp;')
                         .replace(/</g, '&lt;')
                         .replace(/>/g, '&gt;')
                         .replace(/"/g, '&quot;')
                         .replace(/'/g, '&#039;');

    // 恢复HTML标签（标题标签）
    formatted = formatted.replace(/&lt;(\/?)h([1-6])&gt;/g, '<$1h$2>');

    // 处理代码块 ```code```
    formatted = formatted.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>');

    // 处理行内代码 `code`
    formatted = formatted.replace(/`([^`]+)`/g, '<code>$1</code>');

    // 处理粗体 **text**
    formatted = formatted.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');

    // 处理斜体 *text*
    formatted = formatted.replace(/\*(.*?)\*/g, '<em>$1</em>');

    // 处理无序列表 - item
    formatted = formatted.replace(/^\s*-\s+(.*$)/gm, '<li>$1</li>');
    formatted = formatted.replace(/(<li>.*<\/li>)/gs, '<ul>$1</ul>');

    // 处理有序列表 1. item
    formatted = formatted.replace(/^\s*\d+\.\s+(.*$)/gm, '<li>$1</li>');
    formatted = formatted.replace(/(<li>.*<\/li>)/gs, '<ol>$1</ol>');

    // 处理链接 [text](url)
    formatted = formatted.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>');

    // 将换行符转换为HTML换行
    formatted = formatted.replace(/\n/g, '<br>');

    return formatted;
}

// 测试用例
const testContent = `我需要查询2024年2月纽约曼哈顿区的交通事故数据。让我调用交通数据查询工具来获取相关信息。

# 2024年2月交通事故数据分析

根据交通数据查询，2024年2月纽约市共发生6,841起交通事故。以下是详细分析：

## 事故概况
- 总事故数量: 6,841起
- 数据覆盖: 包含曼哈顿、布鲁克林、皇后区等多个行政区
- 时间分布: 从2月1日开始的事故记录

## 事故类型分析
1. 车辆类型：
   - 轿车(Sedan)是最常见的涉事车型
   - 其他常见车型包括：出租车、皮卡、SUV等

## 主要致因因素
- 主要因素：驾驶员疏忽/分心、交通控制违规、超速行驶
- 其他因素：车辆机械问题、未明示因素

## 建议
1. 加强对分心驾驶的监管
2. 在事故高发路段增设安全警示标识
3. 针对骑行者安全加强交通教育`;

console.log('=== 原始输入 ===');
console.log(testContent);

console.log('\n=== 转换结果 ===');
const result = formatMessage(testContent);
console.log(result);

console.log('\n=== 检查标题转换 ===');
// 检查是否包含原始的 # 符号
if (result.includes('# ') || result.includes('## ') || result.includes('### ')) {
    console.log('❌ 错误：标题没有被正确转换，仍然包含原始的 # 符号');
} else {
    console.log('✅ 成功：标题已被正确转换为 HTML 标签');
}

// 检查是否包含 HTML 标题标签
if (result.includes('<h1>') && result.includes('<h2>')) {
    console.log('✅ 成功：检测到 HTML 标题标签');
} else {
    console.log('❌ 错误：没有检测到 HTML 标题标签');
}
