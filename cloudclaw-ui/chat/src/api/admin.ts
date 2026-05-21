// Re-export admin API functions from chat.ts for backward compatibility
// with the admin view components copied into the chat project.
import { adminApi } from './chat'

export const getUsers = adminApi.getUsers
export const createUser = adminApi.createUser
export const updateUser = adminApi.updateUser
export const deleteUser = adminApi.deleteUser

export const getAgents = adminApi.getAgents
export const getAgent = adminApi.getAgent
export const createAgent = adminApi.createAgent
export const updateAgent = adminApi.updateAgent
export const deleteAgent = adminApi.deleteAgent

export const getMcpServers = adminApi.getMcpServers
export const createMcpServer = adminApi.createMcpServer
export const updateMcpServer = adminApi.updateMcpServer
export const deleteMcpServer = adminApi.deleteMcpServer
export const testMcpServer = adminApi.testMcpServer

export const getSkills = adminApi.getSkills
export const uploadSkillZip = adminApi.uploadSkillZip
export const updateSkill = adminApi.updateSkill
export const deleteSkill = adminApi.deleteSkill
export const getSkillFiles = adminApi.getSkillFiles
export const getSkillFile = adminApi.getSkillFile
export const saveSkillFile = adminApi.saveSkillFile
export const deleteSkillFile = adminApi.deleteSkillFile

export const getLlmProviders = adminApi.getLlmProviders
export const createLlmProvider = adminApi.createLlmProvider
export const updateLlmProvider = adminApi.updateLlmProvider
export const deleteLlmProvider = adminApi.deleteLlmProvider
export const getLlmModels = adminApi.getLlmModels
export const createLlmModel = adminApi.createLlmModel
export const updateLlmModel = adminApi.updateLlmModel
export const deleteLlmModel = adminApi.deleteLlmModel
export const getLlmCredentials = adminApi.getLlmCredentials
export const createLlmCredential = adminApi.createLlmCredential
export const updateLlmCredential = adminApi.updateLlmCredential
export const deleteLlmCredential = adminApi.deleteLlmCredential

export const getUsageStats = adminApi.getUsageStats
export const getSessionStats = adminApi.getSessionStats
export const getLogs = adminApi.getLogs

export const getSandboxes = adminApi.getSandboxes
export const forceCloseSandbox = adminApi.forceCloseSandbox
export const cleanOrphanSandboxes = adminApi.cleanOrphanSandboxes

export const getSandboxProviders = adminApi.getSandboxProviders
export const createSandboxProvider = adminApi.createSandboxProvider
export const updateSandboxProvider = adminApi.updateSandboxProvider
export const deleteSandboxProvider = adminApi.deleteSandboxProvider

export { default } from './index'
