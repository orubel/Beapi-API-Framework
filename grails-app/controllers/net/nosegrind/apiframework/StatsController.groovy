package net.nosegrind.apiframework

import java.sql.Timestamp
import java.text.SimpleDateFormat

class StatsController{
	
	def springSecurityService
	def statsService

	LinkedHashMap show() {
		Integer time = System.currentTimeMillis()
		Integer day = (Integer) time/((1000*60*60*24)+1)
		switch(params.type){
			case 3:
				// 12 months
				def year = statsService.getStatsByYear(day)
				return [stats:year]
				break
			case 2:
				// 4 weeks / 1 month
				def month = statsService.getStatsByMonth(day)
				return [stats:month]
				break
			case 1:
				// 7 days / 1 week
				def week = statsService.getStatsByWeek(day)
				return [stats:week]
				break
			case 0:
			default:
				// 1 day
				def today = statsService.getStats(day)
				List hrs = ['1AM','2AM','3AM','4AM','5AM','6AM','7AM','8AM','9AM','10AM','11AM','12AM','1PM','2PM','3PM','4PM','5PM','6PM','7PM','8PM','9PM','10PM','11PM','12PM']
				LinkedHashMap apiTotals = ['1AM':0,'2AM':0,'3AM':0,'4AM':0,'5AM':0,'6AM':0,'7AM':0,'8AM':0,'9AM':0,'10AM':0,'11AM':0,'12AM':0,'1PM':0,'2PM':0,'3PM':0,'4PM':0,'5PM':0,'6PM':0,'7PM':0,'8PM':0,'9PM':0,'10PM':0,'11PM':0,'12PM':0]
				LinkedHashMap codeTotals = [:]
				LinkedHashMap userTotals = [:]

				today.each() { it ->
					Timestamp tstamp = new Timestamp(it[2])
					Date date = new Date(tstamp.getTime())
					SimpleDateFormat sdf = new SimpleDateFormat("kk")
					Integer hr = sdf.format(date).toInteger()

					String authority = springSecurityService.principal.authorities*.authority[0]

					if(apiTotals["${hrs[hr]}"]==null) {
						apiTotals["${hrs[hr]}"] = 0
					}else{
						apiTotals["${hrs[hr]}"] += 1
					}

					if(codeTotals["${it[1]}"]==null){
						codeTotals["${it[1]}"] = 0
					}else{
						codeTotals["${it[1]}"] += 1
					}

					if(userTotals["${authority}"]==null){
						userTotals["${authority}"] = 0
					}else{
						userTotals["${authority}"] += 1
					}
				}

				return [stats:['apiTotals':apiTotals,'codeTotals':codeTotals,'userTotals':userTotals]]
				break
		}

	}
}
