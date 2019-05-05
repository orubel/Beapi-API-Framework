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

import java.io.File

import grails.util.BuildSettings
import org.apache.commons.io.IOUtils

import groovy.text.Template
import groovy.text.GStringTemplateEngine



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
	LinkedHashMap createData = [:]
	LinkedHashMap updateData = [:]
	LinkedHashMap data = [:]

	boolean handle(ExecutionContext ctx) {
		File baseDirFile = ctx.baseDir
		String absPath = baseDirFile.getAbsolutePath()

		println "### Bootstrapping Controller Files ..."

		String logicalName
		String realName
		String packageName

		// create data based on domains
		def domains = Holders.grailsApplication.getArtefacts("Domain")


		domains.each() { it ->
			try {
				logicalName = it.getLogicalPropertyName()
				realName = it.getName() as String

				def domain = Holders.grailsApplication.getArtefactByLogicalPropertyName('Domain', logicalName)
				packageName = domain.getPackageName() as String
				packageName = packageName.replaceAll("\\.", "/")

				if (!controllerExists("grails-app/controllers/${packageName}/${realName}Controller.groovy")) {
					this.data[realName] = [:]

					this.data[realName]['packageName'] = packageName

					this.data[realName]['realPackageName'] = domain.getPackageName()

					String contDir = "${absPath}/grails-app/controllers/${packageName}"

					checkDirectory("grails-app/controllers/${packageName}")

					LinkedHashMap controllerList = getControllers(contDir, packageName)


					def sessionFactory = Holders.grailsApplication.mainContext.sessionFactory
					ClassMetadata hibernateMetaClass = sessionFactory.getClassMetadata(it.clazz)

					String id = hibernateMetaClass.getIdentifierPropertyName() as String
					if (id != null) {
						this.data[realName]["${hibernateMetaClass.getIdentifierPropertyName()}"] = hibernateTypeConverter("${hibernateMetaClass.getIdentifierType().getClass()}")
					}

					String[] keys = hibernateMetaClass.getKeyColumnNames()
					keys.each() {
						if (it != 'null' && it != id) {
							this.data[realName][it] = ['Long': 'java.lang.Long']
						}
					}


					def domainProperties = hibernateMetaClass.getPropertyNames()

					domainProperties.each() { it2 ->

						List ignoreList = ['constrainedProperties', 'gormPersistentEntity', 'properties', 'async', 'gormDynamicFinders', 'all', 'attached', 'class', 'constraints', 'reports', 'dirtyPropertyNames', 'errors', 'dirty', 'transients', 'count']
						String name = it2

						// create data and type map
						if (!ignoreList.contains(it2)) {

							if (name != 'null') {
								this.data[realName][name] = hibernateTypeConverter((String) hibernateMetaClass.getPropertyType(it2).getClass())
							}

						}
					}
				}
			}catch(Exception e){
				println("FAILED at domains: "+e)
			}

		}



		LinkedHashMap params = [:]

		this.data.each { k, v ->

				String importedClasses

				v.each { k2, v2 ->
					//add params to list for 'create/update'
					if (k2 != 'packageName') {
						println("DOES NOT EQUAL PACKAGENAME: ")

							//v2.each { k3, v3 ->

								try {
									if (!['id', 'version'].contains(k2)) {
										if (!this.createData[k]) {
											println("CreateData:" + k)
											this.createData[k] = []
										}

										this.createData[k].add("${k2} : \"\${params.${k2}}\"")

									}else{
										println("NOT CREATING DATA")
									}
								}catch(Exception e){
									println("FAILED at createdata: "+e)
								}

								try {
									if (!['version'].contains(k2)) {
										if (!this.updateData[k]) {
											this.updateData[k] = []
										}

										this.updateData[k].add("${k2} : \"\${params.${k2}}\"")

									}
								}catch(Exception e){
									println("FAILED at updatedata: "+e)
								}
								// enforce type may be optional; no enforcement on first pass but save this for future implementation
								/*
								params[k2] = "\$\{params.${k2}\} as ${k3}"
								def grp = (v3 =~ /java.lang\.(.*)/)
								if(!grp.hasGroup()){
									importedClasses += "${v3}\n"
								}
								*/

							//}

					}

				}


		}

		//this.createData.each{
		//	println("createData: "+it)
		//}

		String basedir = BuildSettings.BASE_DIR

		Map templateAttributes = [:]
		this.data.each { k, v ->


				def projectDir = "${basedir}/grails-app/controllers/${this.data[k]['packageName']}"

				String createData = ""
				int i = 1
				this.createData[k].each{
					createData += it
					if(i!=this.createData[k].size()) {
						createData += ","
					}
					i++
				}

				String updateData = ""
				i = 1
				this.updateData[k].each{
					updateData += it
					if(i!=this.updateData[k].size()) {
						updateData += ","
					}
					i++
				}

			try{
				templateAttributes = [
						packageName     : "${this.data[k]['realPackageName']}",
						realClassName   : "${k}",
						importedClasses : "",
						logicalClassName: "${k[0].toLowerCase() + k.substring(1)}",
						createData      : "${createData}",
						updateData      : "${updateData}",
				]
				writeFile('templates/controllers/Controller.groovy.template', "${projectDir}/${k}Controller.groovy", templateAttributes, absPath)
				//generateFile(this.data[k]['packageName'], k, 'grails-app/controllers', templateAttributes)
			}catch(Exception e){
				println("FAILED at formatting data: "+e)
			}
		}

		//println("data:" + data)

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

	boolean controllerExists(String path){
		def cfile = new File(path)
		if (cfile.exists()) {
			return true
		}
		return false
	}

	void writeFile(String inPath, String outPath, LinkedHashMap attribs, String absPath){
		String pluginDir = new File(getClass().protectionDomain.codeSource.location.path).path
		def plugin = new File(pluginDir)
		try {
			if (plugin.isFile() && plugin.name.endsWith("jar")) {
				JarFile jar = new JarFile(plugin)
				JarEntry entry = jar.getEntry(inPath)
				InputStream inStream = jar.getInputStream(entry)
				OutputStream out = new FileOutputStream(outPath)
				int c;
				while ((c = inStream.read()) != -1) {
					out.write(c)
				}
				inStream.close()
				out.close()
				jar.close()

				def templateFile = new File(outPath)
				def engine = new groovy.text.GStringTemplateEngine()
				def template = engine.createTemplate(templateFile).make(attribs)
				println template.toString()

			}
		}catch(Exception e){
			println("FAILED at writefile :"+e)
		}
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

