/*
 * Copyright 2013-2019 Beapi.io
 * API Chaining(R) 2019 USPTO
 *
 * Licensed under the MPL-2.0 License;
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.api.framework

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
//import org.springframework.context.annotation.ComponentScan
import javax.servlet.*;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration


//@ComponentScan
//@EnableAutoConfiguration(exclude = [SecurityFilterAutoConfiguration])
@EnableAutoConfiguration
class Application extends GrailsAutoConfiguration {
	
    static void main(String[] args) {
        GrailsApp.run(Application)
    }

}
