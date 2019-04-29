package net.nosegrind.apiframework


import java.beans.BeanInfo
import java.beans.PropertyDescriptor
import java.beans.Introspector
import java.lang.reflect.InvocationTargetException
import groovy.transform.CompileStatic

/**
 *
 * Params Object. Used in conjunction with ParamsDescriptor in creating params object for use by ApiDescriptor
 * @author Owen Rubel
 *
 * @see ParamsDescriptor
 * @see ApiDescriptor
 *
 */
@CompileStatic
class ApiParams{

	ParamsDescriptor param
	
	private static final INSTANCE = new ApiParams()
	
	static getInstance(){ return INSTANCE }

	/**
	 * Empty Constructor
	 */
	private ApiParams() {}

	
	Map toObject(){
		Map<String, Object> result = new HashMap<String, Object>()
		BeanInfo info = Introspector.getBeanInfo(param.getClass())
		for (PropertyDescriptor descriptor : info.getPropertyDescriptors()) {
			try{
	            Object propertyValue = descriptor.getReadMethod().invoke(param)
	            if (propertyValue != null) {
					if(!['metaClass','class','errors','values'].contains(descriptor.getName())){
						result.put(descriptor.getName(),propertyValue)
					}
	            }
			}catch (final IllegalArgumentException e){
				throw new Exception("[ApiParams :: toObject] : IllegalArgumentException - full stack trace follows:",e)
			}catch (final IllegalAccessException e){
				throw new Exception("[ApiParams :: toObject] : IllegalAccessException - full stack trace follows:",e)
			}catch (final InvocationTargetException e){
				throw new Exception("[ApiParams :: toObject] : InvocationTargetException - full stack trace follows:",e)
			}
		}
		
		return result
	}

	/**
	 *
	 * @param data
	 * @return
	 */
	ApiParams setMockData(String data){
		this.param.mockData = data
		return this
	}
	
	ApiParams setDescription(String data){
		this.param.description = data
		return this
	}

	ApiParams setKey(String data){
		this.param.keyType = data
		return this
	}


	ApiParams hasParams(ParamsDescriptor[] values){
		this.param.values = values
		return this
	}

	ApiParams setReference(String data){
		this.param.idReferences = data
		return this
	}

	ApiParams setParam(String type,String name){
		this.param = new ParamsDescriptor(paramType:"${type}",name:"${name}")
		return this
	}
}
