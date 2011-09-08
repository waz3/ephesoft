/********************************************************************************* 
* Ephesoft is a Intelligent Document Capture and Mailroom Automation program 
* developed by Ephesoft, Inc. Copyright (C) 2010-2011 Ephesoft Inc. 
* 
* This program is free software; you can redistribute it and/or modify it under 
* the terms of the GNU Affero General Public License version 3 as published by the 
* Free Software Foundation with the addition of the following permission added 
* to Section 15 as permitted in Section 7(a): FOR ANY PART OF THE COVERED WORK 
* IN WHICH THE COPYRIGHT IS OWNED BY EPHESOFT, EPHESOFT DISCLAIMS THE WARRANTY 
* OF NON INFRINGEMENT OF THIRD PARTY RIGHTS. 
* 
* This program is distributed in the hope that it will be useful, but WITHOUT 
* ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
* FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more 
* details. 
* 
* You should have received a copy of the GNU Affero General Public License along with 
* this program; if not, see http://www.gnu.org/licenses or write to the Free 
* Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 
* 02110-1301 USA. 
* 
* You can contact Ephesoft, Inc. headquarters at 111 Academy Way, 
* Irvine, CA 92617, USA. or at email address info@ephesoft.com. 
* 
* The interactive user interfaces in modified source and object code versions 
* of this program must display Appropriate Legal Notices, as required under 
* Section 5 of the GNU Affero General Public License version 3. 
* 
* In accordance with Section 7(b) of the GNU Affero General Public License version 3, 
* these Appropriate Legal Notices must retain the display of the "Ephesoft" logo. 
* If the display of the logo is not reasonably feasible for 
* technical reasons, the Appropriate Legal Notices must display the words 
* "Powered by Ephesoft". 
********************************************************************************/ 

package com.ephesoft.dcma.da.common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.commons.lang.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.ephesoft.dcma.da.domain.BatchClass;
import com.ephesoft.dcma.da.domain.BatchClassDynamicPluginConfig;
import com.ephesoft.dcma.da.domain.BatchClassModule;
import com.ephesoft.dcma.da.domain.BatchClassModuleConfig;
import com.ephesoft.dcma.da.domain.BatchClassPlugin;
import com.ephesoft.dcma.da.domain.BatchClassPluginConfig;
import com.ephesoft.dcma.da.domain.DocumentType;
import com.ephesoft.dcma.da.domain.FieldType;
import com.ephesoft.dcma.da.domain.FunctionKey;
import com.ephesoft.dcma.da.domain.KVExtraction;
import com.ephesoft.dcma.da.domain.KVPageProcess;
import com.ephesoft.dcma.da.domain.PageType;
import com.ephesoft.dcma.da.domain.RegexValidation;
import com.ephesoft.dcma.da.domain.TableColumnsInfo;
import com.ephesoft.dcma.da.domain.TableInfo;
import com.ephesoft.dcma.da.service.BatchClassService;

public class UpgradePatchPreparation {

	private static final Logger log = LoggerFactory.getLogger(UpgradePatchPreparation.class);

	private static final String PROPERTY_FILE = "db-patch";
	private static final String META_INF_PATH = "META-INF\\dcma-data-access";

	private static final String SERIALIZATION_EXT = ".ser";

	private static String upgradePatchFolderPath = null;

	private static HashMap<Integer, ArrayList<BatchClassPluginConfig>> pluginIdVsBatchPluginConfigList = new HashMap<Integer, ArrayList<BatchClassPluginConfig>>();

	private static HashMap<String, ArrayList<BatchClassModule>> batchClassNameVsModulesMap = new HashMap<String, ArrayList<BatchClassModule>>();

	private static HashMap<String, ArrayList<BatchClassPlugin>> batchClassNameVsPluginsMap = new HashMap<String, ArrayList<BatchClassPlugin>>();

	private static HashMap<Long, ArrayList<BatchClassModuleConfig>> moduleIdVsBatchClassModuleConfigMap = new HashMap<Long, ArrayList<BatchClassModuleConfig>>();

	private static HashMap<String, ArrayList<BatchClass>> batchClassNameVsBatchClassMap = new HashMap<String, ArrayList<BatchClass>>();

	private static Properties loadProperties(String propertyName) throws IOException {
		String filePath = META_INF_PATH + File.separator + propertyName + ".properties";
		Properties properties = new Properties();
		InputStream propertyInStream = null;
		try {
			propertyInStream = new ClassPathResource(filePath).getInputStream();

			properties.load(propertyInStream);
		} finally {
			propertyInStream.close();
		}
		return properties;
	}

	public static void createDBPatch(BatchClassService service) {
		String pluginInfo = null;
		String pluginConfigInfo = null;
		String moduleInfo = null;
		String batchClassInfo = null;
		String moduleConfigInfo = null;
		try {
			Properties props = loadProperties(PROPERTY_FILE);

			upgradePatchFolderPath = props.getProperty("upgradePatch.folder");
			if (upgradePatchFolderPath == null || upgradePatchFolderPath.isEmpty()) {
				log.error("Patch folder not specified. Unable to complete patch creation.");
				return;
			}

			moduleInfo = props.getProperty("upgradePatch.module");
			if (moduleInfo != null && !moduleInfo.trim().isEmpty()) {
				createPatchForModule(service, moduleInfo);
			}

			pluginInfo = props.getProperty("upgradePatch.plugin");
			if (pluginInfo != null && !pluginInfo.trim().isEmpty()) {
				createPatchForPlugin(service, pluginInfo);
			}

			pluginConfigInfo = props.getProperty("upgradePatch.plugin_property");
			if (pluginConfigInfo != null && !pluginConfigInfo.isEmpty()) {
				createPatchForPluginConfig(service, pluginConfigInfo);
			}

			moduleConfigInfo = props.getProperty("upgradePatch.module_property");
			if (moduleConfigInfo != null && !moduleConfigInfo.isEmpty()) {
				createPatchForBatchClassModuleConfigs(service);
			}

			batchClassInfo = props.getProperty("upgradePatch.batch_class");
			if (batchClassInfo != null && !batchClassInfo.isEmpty()) {
				createPatchForBatchClass(service, batchClassInfo);
			}

		} catch (IOException e) {
			log.error("Unable to load properties file.", e);
		}
	}

	private static void createPatchForPlugin(BatchClassService service, String pluginInfo) {

		StringTokenizer pluginTokens = new StringTokenizer(pluginInfo, ";");
		while (pluginTokens.hasMoreTokens()) {
			String pluginToken = pluginTokens.nextToken();
			StringTokenizer pluginConfigTokens = new StringTokenizer(pluginToken, ",");
			String batchClassIdentifier = null;
			String moduleId = null;
			String pluginId = null;
			try {
				batchClassIdentifier = pluginConfigTokens.nextToken();
				moduleId = pluginConfigTokens.nextToken();
				pluginId = pluginConfigTokens.nextToken();
				BatchClassPlugin createdPlugin = createPatch(batchClassIdentifier, moduleId, pluginId, service);
				if (createdPlugin != null) {
					BatchClass batchClass = service.getBatchClassByIdentifier(batchClassIdentifier);
					String key = batchClass.getName() + "," + moduleId;
					ArrayList<BatchClassPlugin> pluginsList = batchClassNameVsPluginsMap.get(key);
					if (pluginsList == null) {
						pluginsList = new ArrayList<BatchClassPlugin>();
						batchClassNameVsPluginsMap.put(key, pluginsList);
					}
					pluginsList.add(createdPlugin);
				}

			} catch (NoSuchElementException e) {
				log.info("Incomplete data specifiedin properties file.", e);
			}
		}

		try {
			File serializedExportFile = new File(upgradePatchFolderPath + File.separator + "PluginUpdate" + SERIALIZATION_EXT);
			SerializationUtils.serialize(batchClassNameVsPluginsMap, new FileOutputStream(serializedExportFile));
		} catch (FileNotFoundException e) {
			// Unable to read serializable file
			log.error("Error occurred while creating the serializable file." + e.getMessage(), e);
		}
	}

	private static void createPatchForPluginConfig(BatchClassService service, String pluginConfigInfo) {
		StringTokenizer pluginTokens = new StringTokenizer(pluginConfigInfo, ";");
		while (pluginTokens.hasMoreTokens()) {
			String pluginToken = pluginTokens.nextToken();
			StringTokenizer pluginConfigTokens = new StringTokenizer(pluginToken, ",");
			String pluginId = null;
			String pluginConfigId = null;
			try {
				pluginId = pluginConfigTokens.nextToken();
				pluginConfigId = pluginConfigTokens.nextToken();
				createPatch(pluginId, pluginConfigId, service);

			} catch (NoSuchElementException e) {
				log.error("Incomplete data specified in properties file.", e);
			}
		}

		try {
			File serializedExportFile = new File(upgradePatchFolderPath + File.separator + "PluginConfigUpdate" + SERIALIZATION_EXT);
			SerializationUtils.serialize(pluginIdVsBatchPluginConfigList, new FileOutputStream(serializedExportFile));
		} catch (FileNotFoundException e) {
			// Unable to read serializable file
			log.error("Error occurred while creating the serializable file." + e.getMessage(), e);
		}
	}

	private static void createPatchForModule(BatchClassService service, String moduleInfo) {
		StringTokenizer moduleTokens = new StringTokenizer(moduleInfo, ";");
		while (moduleTokens.hasMoreTokens()) {
			String moduleToken = moduleTokens.nextToken();
			StringTokenizer pluginConfigTokens = new StringTokenizer(moduleToken, ",");
			String batchClassName = null;
			String moduleId = null;
			try {
				batchClassName = pluginConfigTokens.nextToken();
				moduleId = pluginConfigTokens.nextToken();
				BatchClassModule createdModule = createPatchForModule(batchClassName, moduleId, service);
				if (createdModule != null) {
					BatchClass batchClass = service.getBatchClassByIdentifier(batchClassName);
					ArrayList<BatchClassModule> bcmList = batchClassNameVsModulesMap.get(batchClass.getName());
					if (bcmList == null) {
						bcmList = new ArrayList<BatchClassModule>();
						batchClassNameVsModulesMap.put(batchClass.getName(), bcmList);
					}
					bcmList.add(createdModule);
				}

			} catch (NoSuchElementException e) {
				log.error("Incomplete data specified in properties file.", e);
			}
		}

		try {
			File serializedExportFile = new File(upgradePatchFolderPath + File.separator + "ModuleUpdate" + SERIALIZATION_EXT);
			SerializationUtils.serialize(batchClassNameVsModulesMap, new FileOutputStream(serializedExportFile));
		} catch (FileNotFoundException e) {
			// Unable to read serializable file
			log.error("Error occurred while creating the serializable file." + e.getMessage(), e);
		}

	}

	private static void createPatchForBatchClass(BatchClassService service, String batchClassInfo) {
		StringTokenizer batchClassTokens = new StringTokenizer(batchClassInfo, ";");
		while (batchClassTokens.hasMoreTokens()) {
			String batchClassName = batchClassTokens.nextToken();
			try {
				BatchClass createdBatchClass = createPatchForBatchClass(batchClassName, service);
				if (createdBatchClass != null) {
					BatchClass batchClass = service.getBatchClassByIdentifier(batchClassName);
					ArrayList<BatchClass> batchClassList = batchClassNameVsBatchClassMap.get(batchClass.getName());
					if (batchClassList == null) {
						batchClassList = new ArrayList<BatchClass>();
						batchClassNameVsBatchClassMap.put(batchClass.getName(), batchClassList);
					}
					batchClassList.add(createdBatchClass);
				}

			} catch (NoSuchElementException e) {
				log.error("Incomplete data specified in properties file.", e);
			}
		}

		try {
			File serializedExportFile = new File(upgradePatchFolderPath + File.separator + "BatchClassUpdate" + SERIALIZATION_EXT);
			SerializationUtils.serialize(batchClassNameVsBatchClassMap, new FileOutputStream(serializedExportFile));
		} catch (FileNotFoundException e) {
			// Unable to read serializable file
			log.error("Error occurred while creating the serializable file." + e.getMessage(), e);
		}
	}

	private static BatchClassModule createPatchForModule(String batchClassIdentifier, String moduleName,
			BatchClassService batchClassService) {

		BatchClassModule createdModule = null;
		try {
			BatchClass batchClass = batchClassService.getLoadedBatchClassByIdentifier(batchClassIdentifier);
			batchClassService.evict(batchClass);
			List<BatchClassModule> bcms = batchClass.getBatchClassModules();
			for (BatchClassModule bcm : bcms) {
				if (bcm.getModule().getName().equalsIgnoreCase(moduleName)) {
					createdModule = bcm;
					break;
				}
			}
			if (createdModule != null) {
				createdModule.setId(0);
				createdModule.setBatchClass(null);
				List<BatchClassPlugin> newBatchClassPluginsList = new ArrayList<BatchClassPlugin>();
				for (BatchClassPlugin bcp : createdModule.getBatchClassPlugins()) {
					preparePluginForSerialization(bcp);
					newBatchClassPluginsList.add(bcp);
				}
				createdModule.setBatchClassPlugins(newBatchClassPluginsList);
			}

		} catch (NumberFormatException e) {
			log.error("Module Id should be numeric." + e.getMessage(), e);
		}
		return createdModule;
	}

	private static BatchClass createPatchForBatchClass(String batchClassIdentifier, BatchClassService batchClassService) {

		BatchClass createdBatchClass = null;
		BatchClassModule createdBatchClassModule = null;
		List<BatchClassModule> batchClassModules = new ArrayList<BatchClassModule>();
		try {
			BatchClass batchClass = batchClassService.getLoadedBatchClassByIdentifier(batchClassIdentifier);
			batchClassService.evict(batchClass);

			// preparing to copy batch class modules..
			List<BatchClassModule> bcms = batchClass.getBatchClassModules();
			for (BatchClassModule bcm : bcms) {
				String moduleName = bcm.getModule().getName();
				if (moduleName != null) {
					createdBatchClassModule = createPatchForModule(batchClassIdentifier, moduleName, batchClassService);
					if (createdBatchClassModule != null) {
						List<BatchClassPlugin> newBatchClassPluginsList = new ArrayList<BatchClassPlugin>();
						for (BatchClassPlugin bcp : createdBatchClassModule.getBatchClassPlugins()) {
							preparePluginForSerialization(bcp);
							newBatchClassPluginsList.add(bcp);
						}
						createdBatchClassModule.setBatchClassPlugins(newBatchClassPluginsList);
						batchClassModules.add(createdBatchClassModule);
					}
				}
			}

			// preparing to copy document types..
			List<DocumentType> documentTypes = batchClass.getDocumentTypes();
			List<DocumentType> newDocumentType = new ArrayList<DocumentType>();
			for (DocumentType documentType : documentTypes) {
				newDocumentType.add(documentType);
				documentType.setId(0);
				documentType.setBatchClass(null);
				documentType.setIdentifier(null);
				preparePageTypeForSerialization(documentType);
				prepareFieldTypeForSerialization(documentType);
				prepareTableInfoForSerialization(documentType);
				prepareFunctionKeyForSerialization(documentType);
			}
			batchClass.setDocumentTypes(newDocumentType);

			createdBatchClass = batchClass;
			if (createdBatchClass != null) {
				prepareBatchClassForSerialization(createdBatchClass, batchClassModules, newDocumentType);
			}

		} catch (NumberFormatException e) {
			log.error("Module Id should be numeric." + e.getMessage(), e);
		}
		return createdBatchClass;
	}

	private static BatchClassPlugin createPatch(String batchClassIdentifier, String moduleId, String pluginId,
			BatchClassService batchClassService) {
		BatchClassPlugin createdPlugin = null;
		try {
			int modId = Integer.parseInt(moduleId);
			int plgId = Integer.parseInt(pluginId);
			BatchClass batchClass = batchClassService.getLoadedBatchClassByIdentifier(batchClassIdentifier);
			batchClassService.evict(batchClass);
			List<BatchClassModule> mods = batchClass.getBatchClassModules();
			List<BatchClassPlugin> plugins = null;
			boolean pluginFound = false;
			for (BatchClassModule bcm : mods) {
				if (bcm.getModule().getId() == modId) {
					plugins = bcm.getBatchClassPlugins();
					for (BatchClassPlugin bcp : plugins) {
						if (bcp.getPlugin().getId() == plgId) {
							createdPlugin = bcp;
							pluginFound = true;
							break;
						}
					}
					if (pluginFound) {
						break;
					}
				}
			}

			// preparing the plugin for addition to batch classes.
			if (createdPlugin != null) {
				preparePluginForSerialization(createdPlugin);
			}

		} catch (NumberFormatException e) {
			log.error("Module Id and Plugin Id should be numeric." + e.getMessage(), e);
		}
		return createdPlugin;
	}

	private static void prepareBatchClassForSerialization(BatchClass createdBatchClass, List<BatchClassModule> batchClassModules,
			List<DocumentType> documentTypes) {
		createdBatchClass.setId(0);
		createdBatchClass.setIdentifier(null);
		createdBatchClass.setAssignedGroups(null);
		createdBatchClass.setBatchClassModules(batchClassModules);
		createdBatchClass.setDocumentTypes(documentTypes);
	}

	private static void preparePluginForSerialization(BatchClassPlugin createdPlugin) {
		createdPlugin.setBatchClassModule(null);
		createdPlugin.setId(0);
		ArrayList<BatchClassPluginConfig> newPluginConfigs = new ArrayList<BatchClassPluginConfig>();
		for (BatchClassPluginConfig config : createdPlugin.getBatchClassPluginConfigs()) {
			config.setId(0);
			config.setBatchClassPlugin(null);
			newPluginConfigs.add(config);
			ArrayList<KVPageProcess> newKVPageProcess = new ArrayList<KVPageProcess>();
			for (KVPageProcess kv : config.getKvPageProcesses()) {
				kv.setId(0);
				kv.setBatchClassPluginConfig(null);
				newKVPageProcess.add(kv);
			}
			config.setKvPageProcesses(newKVPageProcess);

		}

		ArrayList<BatchClassDynamicPluginConfig> newDynamicPluginConfigs = new ArrayList<BatchClassDynamicPluginConfig>();
		for (BatchClassDynamicPluginConfig dynamicConfig : createdPlugin.getBatchClassDynamicPluginConfigs()) {
			dynamicConfig.setId(0);
			dynamicConfig.setBatchClassPlugin(null);
			newDynamicPluginConfigs.add(dynamicConfig);
			ArrayList<BatchClassDynamicPluginConfig> newChildren = new ArrayList<BatchClassDynamicPluginConfig>();
			for (BatchClassDynamicPluginConfig child : dynamicConfig.getChildren()) {
				child.setId(0);
				child.setBatchClassPlugin(null);
				child.setParent(null);
				newChildren.add(child);
			}
			dynamicConfig.setChildren(newChildren);
		}

		createdPlugin.setBatchClassPluginConfigs(newPluginConfigs);
	}

	public static void preparePageTypeForSerialization(DocumentType documentType) {
		List<PageType> pages = documentType.getPages();
		List<PageType> newPageTypes = new ArrayList<PageType>();
		for (PageType pageType : pages) {
			newPageTypes.add(pageType);
			pageType.setId(0);
			pageType.setDocType(null);
			pageType.setIdentifier(null);
		}
		documentType.setPages(newPageTypes);
	}

	public static void prepareFieldTypeForSerialization(DocumentType documentType) {
		List<FieldType> fieldTypes = documentType.getFieldTypes();
		List<FieldType> newFieldType = new ArrayList<FieldType>();
		for (FieldType fieldType : fieldTypes) {
			newFieldType.add(fieldType);
			fieldType.setId(0);
			fieldType.setDocType(null);
			fieldType.setIdentifier(null);
			prepareKVExtractionFieldForSerialization(fieldType);
			prepareRegexForSerialization(fieldType);
		}
		documentType.setFieldTypes(newFieldType);
	}

	public static void prepareTableInfoForSerialization(DocumentType documentType) {
		List<TableInfo> tableInfos = documentType.getTableInfos();
		List<TableInfo> newTableInfo = new ArrayList<TableInfo>();
		for (TableInfo tableInfo : tableInfos) {
			newTableInfo.add(tableInfo);
			tableInfo.setId(0);
			tableInfo.setDocType(null);
			prepareTableColumnsInfoForSerialization(tableInfo);
		}
		documentType.setTableInfos(newTableInfo);
	}

	public static void prepareTableColumnsInfoForSerialization(TableInfo tableInfo) {
		List<TableColumnsInfo> tableColumnsInfos = tableInfo.getTableColumnsInfo();
		List<TableColumnsInfo> newTableColumnsInfo = new ArrayList<TableColumnsInfo>();
		for (TableColumnsInfo tableColumnsInfo : tableColumnsInfos) {
			newTableColumnsInfo.add(tableColumnsInfo);
			tableColumnsInfo.setId(0);
		}
		tableInfo.setTableColumnsInfo(newTableColumnsInfo);
	}

	public static void prepareKVExtractionFieldForSerialization(FieldType fieldType) {
		List<KVExtraction> kvExtraction2 = fieldType.getKvExtraction();
		List<KVExtraction> newKvExtraction = new ArrayList<KVExtraction>();
		for (KVExtraction kvExtraction : kvExtraction2) {
			newKvExtraction.add(kvExtraction);
			kvExtraction.setId(0);
			kvExtraction.setFieldType(null);
		}
		fieldType.setKvExtraction(newKvExtraction);
	}

	public static void prepareRegexForSerialization(FieldType fieldType) {
		List<RegexValidation> regexValidations = fieldType.getRegexValidation();
		List<RegexValidation> regexValidations2 = new ArrayList<RegexValidation>();
		for (RegexValidation regexValidation : regexValidations) {
			regexValidations2.add(regexValidation);
			regexValidation.setId(0);
			regexValidation.setFieldType(null);
		}
		fieldType.setRegexValidation(regexValidations2);
	}

	public static void prepareFunctionKeyForSerialization(DocumentType documentType) {
		List<FunctionKey> functionKeys = documentType.getFunctionKeys();
		List<FunctionKey> newFunctionKeys = new ArrayList<FunctionKey>();
		for (FunctionKey functionKey : functionKeys) {
			newFunctionKeys.add(functionKey);
		}
		documentType.setFunctionKeys(newFunctionKeys);

	}

	private static void createPatch(String pluginId, String pluginConfigId, BatchClassService batchClassService) {
		try {
			ArrayList<BatchClassPluginConfig> newPluginConfigs = pluginIdVsBatchPluginConfigList.get(Integer.valueOf(pluginId));
			if (newPluginConfigs == null) {
				newPluginConfigs = new ArrayList<BatchClassPluginConfig>();
				pluginIdVsBatchPluginConfigList.put(Integer.valueOf(pluginId), newPluginConfigs);
			}
			int plgId = Integer.parseInt(pluginId);
			int plgConfId = Integer.parseInt(pluginConfigId);
			boolean bcpcFound = false;
			BatchClassPluginConfig createdBatchClassPluginConfig = null;
			List<BatchClass> batchClasses = batchClassService.getAllLoadedBatchClassExcludeDeleted();
			for (BatchClass batchClass : batchClasses) {
				BatchClass loadedBatchClass = batchClassService.getLoadedBatchClassByIdentifier(batchClass.getIdentifier());
				List<BatchClassModule> batchClassModules = loadedBatchClass.getBatchClassModules();
				for (BatchClassModule bcm : batchClassModules) {
					List<BatchClassPlugin> batchClassPlugins = bcm.getBatchClassPlugins();
					for (BatchClassPlugin bcp : batchClassPlugins) {
						if (bcp.getPlugin().getId() == plgId) {
							List<BatchClassPluginConfig> batchClassPluginConfigs = bcp.getBatchClassPluginConfigs();
							bcpcFound = false;
							for (BatchClassPluginConfig bcpc : batchClassPluginConfigs) {
								if (bcpc.getId() == plgConfId) {
									createdBatchClassPluginConfig = bcpc;
									bcpcFound = true;
									break;
								}
							}

							if (bcpcFound) {
								createdBatchClassPluginConfig.setId(0);
								createdBatchClassPluginConfig.setBatchClassPlugin(null);
								newPluginConfigs.add(createdBatchClassPluginConfig);
								ArrayList<KVPageProcess> newKVPageProcess = new ArrayList<KVPageProcess>();
								for (KVPageProcess kv : createdBatchClassPluginConfig.getKvPageProcesses()) {
									kv.setId(0);
									kv.setBatchClassPluginConfig(null);
									newKVPageProcess.add(kv);
								}
								createdBatchClassPluginConfig.setKvPageProcesses(newKVPageProcess);
							}
						}
					}
				}
			}

		} catch (NumberFormatException e) {
			log.error("Plugin Id and Plugin Config Id should be numeric." + e.getMessage(), e);
		}

	}

	private static void createPatchForBatchClassModuleConfigs(BatchClassService service) {
		List<BatchClass> batchClassList = service.getAllBatchClassesExcludeDeleted();
		if (batchClassList != null && !batchClassList.isEmpty()) {
			BatchClass batchClass = service.getLoadedBatchClassByIdentifier(batchClassList.get(0).getIdentifier());
			service.evict(batchClass);
			List<BatchClassModule> batchClassModules = batchClass.getBatchClassModules();
			for (BatchClassModule bcm : batchClassModules) {
				if (bcm != null && bcm.getBatchClassModuleConfig() != null && !bcm.getBatchClassModuleConfig().isEmpty()) {
					ArrayList<BatchClassModuleConfig> batchClassModuleConfigList = new ArrayList<BatchClassModuleConfig>();
					List<BatchClassModuleConfig> bcmcList = bcm.getBatchClassModuleConfig();
					for (BatchClassModuleConfig bcmc : bcmcList) {
						bcmc.setId(0);
						bcmc.setBatchClassModule(null);
						batchClassModuleConfigList.add(bcmc);
					}
					moduleIdVsBatchClassModuleConfigMap.put(Long.valueOf(bcm.getModule().getId()), batchClassModuleConfigList);
				}
			}

			try {
				File serializedExportFile = new File(upgradePatchFolderPath + File.separator + "ModuleConfigUpdate"
						+ SERIALIZATION_EXT);
				SerializationUtils.serialize(moduleIdVsBatchClassModuleConfigMap, new FileOutputStream(serializedExportFile));
			} catch (FileNotFoundException e) {
				log.error("Error occurred while creating the serializable file." + e.getMessage(), e);
			}
		}
	}
}