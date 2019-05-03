package net.nosegrind.apiframework

import grails.util.Holders
import grails.dev.commands.*
import grails.core.GrailsApplication
import grails.util.Environment
import grails.dev.commands.ApplicationCommand
import grails.dev.commands.ExecutionContext
import org.hibernate.metadata.ClassMetadata

import grails.core.GrailsDomainClass

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.Field

import java.io.File



/**
 *
 * Commandline tool for bootstrapping Controllers based on pre-existing domain classes & IO State files
 *
 * Usage:
 * ./gradlew GenerateController
 *
 * This will generate Controllers classes for all domains
 *
 * ./gradlew GenerateController -Dfile=Person
 *
 * This will generate the Controller class for the domain class 'Person'
 *
 * @see ApiCommLayer
 *
 */
class GenerateControllerCommand implements ApplicationCommand {


	String iostateDir = ""

	boolean handle(ExecutionContext ctx) {
		File baseDirFile = ctx.baseDir
		String absPath = baseDirFile.getAbsolutePath()

		println "### Bootstrapping Controller Files ..."

		String logicalName
		String realName




		// create data based on domains
		def domains = Holders.grailsApplication.getArtefacts("Domain")
		LinkedHashMap data = [:]

		domains.each() { it ->
			logicalName = it.getLogicalPropertyName()
			realName = it.getName()
			data[realName] = [:]

			def domain = Holders.grailsApplication.getArtefactByLogicalPropertyName('Domain', logicalName)
			String packageName = domain.getPackageName()
			packageName = packageName.replaceAll("\\.","/")
			String contDir = "${absPath}/grails-app/controllers/${packageName}"

			checkDirectory("grails-app/controllers/${packageName}")

			LinkedHashMap controllerList = getControllers(contDir, packageName)




			def sessionFactory = Holders.grailsApplication.mainContext.sessionFactory
			ClassMetadata hibernateMetaClass = sessionFactory.getClassMetadata(it.clazz)

			String id = hibernateMetaClass.getIdentifierPropertyName() as String
			if(id!=null) {
				data[realName]["${hibernateMetaClass.getIdentifierPropertyName()}"] = hibernateTypeConverter("${hibernateMetaClass.getIdentifierType().getClass()}")
			}

			String[] keys = hibernateMetaClass.getKeyColumnNames()
			keys.each(){
				if(it!='null' && it!=id){
					data[realName][it] = ['Long':'java.lang.Long']
				}
			}

			//def controller = Holders.grailsApplication.getArtefactByLogicalPropertyName('Controller', logicalName)


			//println(packageName)
			//println("[" + logicalName + "]:" + domain.getConstrainedProperties())
			//def constraints = domain.getConstrainedProperties()


			//if (controller && !reservedNames.contains(logicalName)) {
			//List actions = controller.actions as List


			def domainProperties = hibernateMetaClass.getPropertyNames()

			//List variables = []
			//variables.add("\"id\"")


			domainProperties.each() { it2 ->

				List ignoreList = ['constrainedProperties', 'gormPersistentEntity', 'properties', 'async', 'gormDynamicFinders', 'all', 'attached', 'class', 'constraints', 'reports', 'dirtyPropertyNames', 'errors', 'dirty', 'transients', 'count']
				String name = it2

				//String type = ""
				//String key = ""

				// create data and type map
				if (!ignoreList.contains(it2)) {

					if(name!='null') {
						data[realName][name] = hibernateTypeConverter((String)hibernateMetaClass.getPropertyType(it2).getClass())
					}

					//type = getValueType(thisType)

				}
			}


			/*
			templateAttributes = [
				packageName: "${packageName}",
				realClassName: "${realName}",
				logicalClassName: "${logicalName}",

				createData: "${createData}",
				updateData: "${updateData}",
				requestmapClassName: requestmapModel?.simpleName,
				groupClassName: groupModel?.simpleName,
				groupClassProperty: groupModel?.modelName
			]
			*/

		}

		//}
		//if (logicalName.length() > 0 && values.length() > 0 && uris.length() > 1) {

		//     createTemplate(iostateDir, realName, logicalName, values, uris)
		//}

		println("data:"+data)

		// write templates

		return true
	}

	/**
	 *
	 * @param String absolutePath for project
	 * @param String packageName for domain to match with controllers
	 * @return LinkedHashMap keys are realName of domains and values are name of file of all controllers that exist
	 */
	protected LinkedHashMap getControllers(String path, String packageName){
		LinkedHashMap result = [:]

		String url = "cd ${path} && ls"
		def proc = ['bash','-c',url].execute()
		proc.waitFor()
		def temp = proc.text.split('\n')
		temp.each(){ it ->
			def grp = (it =~ /(.*)Controller.groovy/)
			if(grp.hasGroup()){
				if(!result[grp[0][1]]){
					result[grp[0][1]] = grp[0][0]
				}
			}

		}
		return result
	}

	void checkDirectory(contDir) {
		def ant = new AntBuilder()
		def cfile = new File(contDir)
		if (!cfile.exists()) {
			ant.mkdir(dir: contDir)
		}
		return
	}

	LinkedHashMap hibernateTypeConverter(String type){
		switch(type){
			case 'class org.hibernate.type.CharacterType':
				return ['Character':'java.lang.Character']
				break
			case 'class org.hibernate.type.NumericBooleanType':
			case 'class org.hibernate.type.YesNoType':
			case 'class org.hibernate.type.TrueFalseType':
			case 'class org.hibernate.type.BooleanType':
				return ['Boolean':'java.lang.Boolean']
				break
			case 'class org.hibernate.type.ByteType':
				return ['Byte':'java.lang.Byte']
				break
			case 'class org.hibernate.type.ShortType':
				return ['Short':'java.lang.Short']
				break
			case 'class org.hibernate.type.IntegerTypes':
				return ['Integer':'java.lang.Integer']
				break
			case 'class org.hibernate.type.LongType':
				return ['Long':'java.lang.Long']
				break
			case 'class org.hibernate.type.FloatType':
				return ['Float':'java.lang.Float']
				break
			case 'class org.hibernate.type.DoubleType':
				return ['Double':'java.lang.Double']
				break
			case 'class org.hibernate.type.BigIntegerType':
				return ['BigInteger':'java.math.BigInteger']
				break
			case 'class org.hibernate.type.BigDecimalType':
				return ['BigDecimal':'java.math.BigDecimal']
				break
			case 'class org.hibernate.type.TimestampType':
				return ['Timestamp':'java.sql.Timestamp']
				break
			case 'class org.hibernate.type.TimeType':
				return ['Time':'java.sql.Time']
				break
			case 'class org.hibernate.type.CalendarDateType':
			case 'class org.hibernate.type.DateType':
				return ['Date':'java.sql.Date']
				break
			case 'class org.hibernate.type.CalendarType':
				return ['Calendar':'java.util.Calendar']
				break
			case 'class org.hibernate.type.CurrencyType':
				return ['Currency':'java.util.Currency']
				break
			case 'class org.hibernate.type.LocaleType':
				return ['Locale':'java.util.Locale']
				break
			case 'class org.hibernate.type.TimeZoneType':
				return ['TimeZone':'java.util.TimeZone']
				break
			case 'class org.hibernate.type.UrlType':
				return ['URL':'java.net.URL']
				break
			case 'class org.hibernate.type.ClassType':
				return ['Class':'java.lang.Class']
				break
			case 'class org.hibernate.type.MaterializedBlobType':
			case 'class org.hibernate.type.BlobType':
				return ['Blob':'java.sql.Blob']
				break
			case 'class org.hibernate.type.ClobType':
				return ['Clob':'java.sql.Clob']
				break
			case 'class org.hibernate.type.PostgresUUIDType':
			case 'class org.hibernate.type.UUIDBinaryType':
				return ['UUID':'java.util.UUID']
				break
			case 'class org.hibernate.type.TextType':
			case 'class org.hibernate.type.StringType':
			default:
				return ['String':'java.lang.String']
				break
		}
	}


}

