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

import java.util.Set;

import org.apache.commons.text.WordUtils;

import ortus.boxlang.mail.util.MailKeys;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.BoxComponent;
import ortus.boxlang.runtime.components.Component;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.validation.Validator;

@BoxComponent( allowsBody = true, requiresBody = true )
public class MailPart extends Component {

	public MailPart() {
		super();
		declaredAttributes = new Attribute[] {
		    new Attribute( Key.type, "string", Set.of(
		        Validator.REQUIRED,
		        Validator.NON_EMPTY
		    ) ), // "mime type"
		    new Attribute( Key.charset, "string", "utf-8" ), // "character encoding"
		    new Attribute( MailKeys.wrapText, "integer" ), // "number"
		};
	}

	/**
	 * Processes a mail part within the body of a mail component
	 *
	 * @param context        The context in which the Component is being invoked
	 * @param attributes     The attributes to the Component
	 * @param body           The body of the Component
	 * @param executionState The execution state of the Component
	 *
	 * @attribute.type The mime type of the mail part
	 *
	 * @attribute.charset The character encoding of the mail part
	 *
	 * @attribute.wrapText The number of characters to wrap the mail part at
	 *
	 */
	public BodyResult _invoke( IBoxContext context, IStruct attributes, ComponentBody body, IStruct executionState ) {
		IStruct parentState = context.findClosestComponent( MailKeys.Mail );
		if ( parentState == null ) {
			throw new RuntimeException( "MailPart must be nested in the body of an Mail component" );
		}
		Integer			wrapText	= parentState.getAsInteger( MailKeys.wrapText );

		StringBuffer	buffer		= new StringBuffer();

		processBody( context, body, buffer );

		attributes.put( Key.result, wrapText != null ? WordUtils.wrap( buffer.toString(), wrapText ) : buffer.toString() );
		// Set our data into the HTTP component for it to use
		parentState.getAsArray( MailKeys.mailParts ).add( attributes );

		return DEFAULT_RETURN;
	}
}
