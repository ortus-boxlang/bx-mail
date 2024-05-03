/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.mail.components;

import ortus.boxlang.mail.util.MailKeys;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.BoxComponent;
import ortus.boxlang.runtime.components.Component;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

@BoxComponent( allowsBody = false )
public class MailParam extends Component {

	public MailParam() {
		super();
		declaredAttributes = new Attribute[] {
		    new Attribute( Key._NAME, "string" ), // "header name"
		    new Attribute( Key.value, "string" ), // "header value"
		    new Attribute( MailKeys.contentID, "string" ), // "content ID"
		    new Attribute( MailKeys.disposition, "string" ), // "disposition type"
		    new Attribute( Key.file, "string" ), // "file path"
		    new Attribute( MailKeys.fileName, "string" ), // "name of the file to be sent as attachment"
		    new Attribute( Key.type, "string" ), // media type"
		};
	}

	/**
	 * Process a mail parameter within the body of a Mail component
	 *
	 * @param context        The context in which the Component is being invoked
	 * @param attributes     The attributes to the Component
	 * @param body           The body of the Component
	 * @param executionState The execution state of the Component
	 *
	 * @attribute.name The header name
	 *
	 * @attribute.value The header value
	 *
	 * @attribute.contentID The content ID
	 *
	 * @attribute.disposition The disposition type
	 *
	 * @attribute.file The file path
	 *
	 * @attribute.fileName The name of the file to be sent as an attachment
	 *
	 * @attribute.type The media type
	 *
	 */
	public BodyResult _invoke( IBoxContext context, IStruct attributes, ComponentBody body, IStruct executionState ) {
		IStruct parentState = context.findClosestComponent( MailKeys.Mail );
		if ( parentState == null ) {
			throw new RuntimeException( "MailParam must be nested in the body of an Mail component" );
		}
		// Set our data into the HTTP component for it to use
		parentState.getAsArray( MailKeys.mailParams ).add( attributes );

		return DEFAULT_RETURN;
	}
}
