package com.ortussolutions.boxlang.mail.components;

import java.util.Set;

import com.ortussolutions.boxlang.mail.util.MailKeys;

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
			throw new RuntimeException( "MailPart must be nested in the body of an Mail component" );
		}

		StringBuffer buffer = new StringBuffer();

		processBody( context, body, buffer );

		attributes.put( Key.result, buffer.toString() );
		// Set our data into the HTTP component for it to use
		parentState.getAsArray( MailKeys.mailParts ).add( attributes );

		return DEFAULT_RETURN;
	}
}
