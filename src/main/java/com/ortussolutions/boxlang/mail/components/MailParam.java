package com.ortussolutions.boxlang.mail.components;

import com.ortussolutions.boxlang.mail.util.MailKeys;

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
	 * An example component that says hello
	 *
	 * @param context        The context in which the Component is being invoked
	 * @param attributes     The attributes to the Component
	 * @param body           The body of the Component
	 * @param executionState The execution state of the Component
	 *
	 * @attribute.name The name of the person greeting us.
	 *
	 * @attribute.location The location of the person.
	 *
	 * @attribute.shout Whether the person is shouting or not.
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
