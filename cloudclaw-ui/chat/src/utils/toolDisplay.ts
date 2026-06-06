/**
 * Tool display utility — maps MCP tool names to user-friendly labels.
 */

export interface ToolDisplayConfig {
  /** Human-readable name (Chinese) */
  label: string
  /** Emoji icon */
  icon: string
  /** Category tag */
  category: string
  /** Parameter key → display hint mapping */
  paramHints: Record<string, string>
}

const TOOL_DISPLAY: Record<string, ToolDisplayConfig> = {
  // ── 高德地图 MCP ──
  maps_around_search: { label: '搜索周边', icon: '🔍', category: '地图', paramHints: { keywords: '搜索', radius: '范围' } },
  maps_text_search: { label: '搜索地点', icon: '📍', category: '地图', paramHints: { keywords: '关键词' } },
  maps_direction_driving: { label: '规划驾车路线', icon: '🚗', category: '地图', paramHints: { origin: '起点', destination: '终点' } },
  maps_direction_transit_integrated: { label: '规划公交路线', icon: '🚌', category: '地图', paramHints: { origin: '起点', destination: '终点' } },
  maps_direction_walking: { label: '规划步行路线', icon: '🚶', category: '地图', paramHints: { origin: '起点', destination: '终点' } },
  maps_direction_bicycling: { label: '规划骑行路线', icon: '🚲', category: '地图', paramHints: { origin: '起点', destination: '终点' } },
  maps_weather: { label: '查询天气', icon: '🌤️', category: '天气', paramHints: { city: '城市' } },
  maps_geo: { label: '地理编码', icon: '🌐', category: '地图', paramHints: { address: '地址' } },
  maps_regeocode: { label: '逆地理编码', icon: '📍', category: '地图' },
  maps_distance: { label: '计算距离', icon: '📏', category: '地图' },
  maps_detail: { label: '查看详情', icon: '📋', category: '地图' },

  // ── 记忆工具 ──
  memory_profile: { label: '记录偏好', icon: '🧠', category: '记忆' },
  memory_session: { label: '记录上下文', icon: '📝', category: '记忆' },

  // ── 沙箱工具 ──
  run_code: { label: '执行代码', icon: '▶️', category: '沙箱', paramHints: { language: '语言' } },
  execute_code: { label: '执行代码', icon: '▶️', category: '沙箱', paramHints: { language: '语言' } },

  // ── Agent 转移工具 ──
  // (transfer_ 前缀的工具由前端单独处理，不在这里映射)
}

/**
 * Get display config for a tool. Falls back to a generated label.
 */
export function getToolDisplay(toolName: string): ToolDisplayConfig {
  if (TOOL_DISPLAY[toolName]) {
    return TOOL_DISPLAY[toolName]
  }
  // Fallback: convert snake_case to Title Case
  const label = toolName
    .replace(/_/g, ' ')
    .replace(/\b\w/g, c => c.toUpperCase())
  return { label, icon: '🔧', category: '工具', paramHints: {} }
}

/**
 * Format a tool call's JSON args into a human-readable one-line description.
 */
export function formatToolCallHuman(toolName: string, argsJson: string): string {
  const config = getToolDisplay(toolName)
  try {
    const args = JSON.parse(argsJson)
    const parts: string[] = []
    for (const [key, hint] of Object.entries(config.paramHints)) {
      if (args[key] !== undefined) {
        const val = args[key]
        if (typeof val === 'string') {
          parts.push(`${hint}「${val}」`)
        } else if (typeof val === 'number') {
          // Heuristic: >= 1000 → km, else raw
          if (key === 'radius' || key.toLowerCase().includes('distance')) {
            parts.push(`${hint} ${val >= 1000 ? (val / 1000) + 'km' : val + 'm'}`)
          } else {
            parts.push(`${hint} ${val}`)
          }
        }
      }
    }
    return parts.length > 0 ? parts.join('，') : config.label
  } catch {
    return config.label
  }
}

/**
 * Format tool result into a short summary for the collapsed view.
 */
export function formatToolResultSummary(toolName: string, resultJson: string): string {
  try {
    const data = JSON.parse(resultJson)
    // Array → "找到 N 个结果"
    if (Array.isArray(data)) {
      return `找到 ${data.length} 个结果`
    }
    // Object with data array
    if (data?.data && Array.isArray(data.data)) {
      return `找到 ${data.data.length} 个结果`
    }
    // Object with pois
    if (data?.pois && Array.isArray(data.pois)) {
      return `找到 ${data.pois.length} 个结果`
    }
    // Object with route
    if (data?.route) {
      return '路线规划完成'
    }
    // Object with lives (weather)
    if (data?.lives || data?.forecasts) {
      return '天气查询完成'
    }
    // Generic object
    if (typeof data === 'object') {
      const keys = Object.keys(data)
      if (keys.length <= 3) return resultJson.slice(0, 100)
    }
    return resultJson.slice(0, 100)
  } catch {
    return resultJson.slice(0, 100)
  }
}
