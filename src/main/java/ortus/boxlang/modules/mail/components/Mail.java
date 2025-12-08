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
package ortus.boxlang.modules.mail.components;

import java.util.Set;

import ortus.boxlang.modules.mail.util.MailKeys;
import ortus.boxlang.modules.mail.util.MailUtil;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.BoxComponent;
import ortus.boxlang.runtime.components.Component;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.validation.Validator;

@BoxComponent( allowsBody = true, requiresBody = true, ignoreEnableOutputOnly = true, autoEvaluateBodyExpressions = true, description = "wraps an encompassing mail message and sends it to the specified recipients" )
public class Mail extends Component {

	public Mail() {
		super();
		declaredAttributes = new Attribute[] {
		    new Attribute( Key.from, "string", Set.of( Validator.REQUIRED ) ), // "e-mail address"
		    new Attribute( Key.to, "string", Set.of( Validator.REQUIRED ) ), // "comma-delimited list"
		    new Attribute( MailKeys.subject, "string", Set.of( Validator.REQUIRED ) ), // "string"
		    new Attribute( MailKeys.bcc, "string" ), // "comma-delimited list"
		    new Attribute( MailKeys.cc, "string" ), // "comma-delimited list"
		    new Attribute( Key.charset, "string", "utf-8" ), // "character encoding"
		    new Attribute( MailKeys.debug, "boolean", false ), // "yes|no"
		    new Attribute( MailKeys.failTo, "string" ), // "e-mail address"
		    new Attribute( MailKeys.mailerid, "string" ), // "header id"
		    new Attribute( MailKeys.mimeAttach, "string" ), // "path of file to attach"
		    new Attribute( Key.password, "string" ), // "string"
		    new Attribute( Key.port, "integer" ), // "integer"
		    new Attribute( Key.priority, "string" ), // "integer or string priority level"
		    new Attribute( MailKeys.remove, "boolean", false ), // "yes|no"
		    new Attribute( MailKeys.replyTo, "string" ), // "e-mail address"
		    new Attribute( Key.server, "string" ), // "SMTP server address"
		    new Attribute( MailKeys.spoolEnable, "boolean", false ), // "yes|no"
		    new Attribute( Key.timeout, "string" ), // "number of seconds"
		    new Attribute( Key.type, "string" ), // "mime type"
		    new Attribute( Key.username, "string" ), // "SMTP user ID"
		    new Attribute( MailKeys.useSSL, "boolean" ), // "yes|no"
		    new Attribute( MailKeys.useTLS, "boolean" ), // "yes|no"
		    new Attribute( MailKeys.wrapText, "integer" ), // "column number"
		    new Attribute( MailKeys.sign, "boolean" ), // "true|false" - we keep this without a default so that the global config can override
		    new Attribute( MailKeys.keystore, "string" ), // "location of keystore"
		    new Attribute( MailKeys.keystorePassword, "string" ), // "password of keystore"
		    new Attribute( MailKeys.keyAlias, "string" ), // "alias of key"
		    new Attribute( MailKeys.keyPassword, "string" ), // "password for private key"
		    new Attribute( MailKeys.encrypt, "boolean", false ), // "true|false"
		    new Attribute( MailKeys.recipientCert, "string" ), // <path to the public key cert>
		    new Attribute( MailKeys.encryptionAlgorithm, "string", "AES256_CBC" ), // "DES_EDE3_CBC, RC2_CBC, AES128_CBC, AES192_CBC, AES256_CBC"
		    new Attribute( MailKeys.IDNAVersion, "integer" ), // DNA encoding"
		    // Query-specific attributes
		    new Attribute( Key.query, "any", Set.of( Validator.NOT_IMPLEMENTED ) ), // "query name"
		    new Attribute( Key.group, "string", Set.of( Validator.NOT_IMPLEMENTED ) ), // "query column"
		    new Attribute( MailKeys.groupCaseSensitive, "boolean", Set.of( Validator.NOT_IMPLEMENTED ) ), // "yes|no"
		    new Attribute( Key.startRow, "integer", 1, Set.of( Validator.NOT_IMPLEMENTED ) ), // "query row number"
		    new Attribute( Key.maxRows, "integer", Set.of( Validator.NOT_IMPLEMENTED ) ), // "integer"
		    // Test only attributes
		    new Attribute( MailKeys.messageVariable, "any" ),
		    new Attribute( MailKeys.messageIdentifier, "any" )
		};

	}

	/**
	 * This component wraps an encompassing mail message and sends it to the specified recipients
	 *
	 * @param context        The context in which the Component is being invoked
	 * @param attributes     The attributes to the Component
	 * @param body           The body of the Component
	 * @param executionState The execution state of the Component
	 *
	 * @attribute.from Sender email address
	 *
	 * @attribute.to Comma-delimited list of recipient email addresses
	 *
	 * @attribute.charset The character encoding of the email
	 *
	 * @attribute.subject The email subject
	 *
	 * @attribute.server Optional SMTP server address
	 *
	 * @attribute.port Optional SMTP server port
	 *
	 * @attribute.username Optional SMTP username
	 *
	 * @attribute.password Optional SMTP password
	 *
	 * @attribute.useSSL Optional true|false for SMTP Connection
	 *
	 * @attribute.useTLS true|false for SMTP TLS Connection
	 *
	 * @attribute.mailerid The header ID of the mailer
	 *
	 * @attribute.mimeAttach path of file to attach
	 *
	 * @attribute.type MIME type of the email
	 *
	 * @attribute.wrapText Wrap text after a certain number of characters has been reached
	 *
	 * @attribute.sign true|false Whether to sign the mail message - requires keystore, keystorePassword, keyAlias, keyPassword
	 *
	 * @attribute.keystore The location of the keystore (Used when signing)
	 *
	 * @attribute.keystorePassword The password of the keystore (Used when signing)
	 *
	 * @attribute.keyAlias The alias of the private key to use for signing (Used when signing)
	 *
	 * @attributes.keyPassword The password for the private key within the keystore (Used when signing)
	 *
	 * @attribute.encrypt true|false Whether to encrypt the mail message - requires recipientCert, encryptionAlgorithm
	 *
	 * @attribute.recipientCert The path to the public key certificate of the recipient (Used when encrypting)
	 *
	 * @attribute.encryptionAlgorithm The encryption algorithm to use (Used when encrypting). One of DES_EDE3_CBC, RC2_CBC, AES128_CBC, AES192_CBC,
	 *                                AES256_CBC
	 *
	 * @attribute.debug true|false Whether to enable debug logging output
	 *
	 * @return An empty body result is returned
	 *
	 */
	public BodyResult _invoke( IBoxContext context, IStruct attributes, ComponentBody body, IStruct executionState ) {

		executionState.put( MailKeys.mailParams, new Array() );
		executionState.put( MailKeys.mailParts, new Array() );

		StringBuffer	buffer		= new StringBuffer();

		BodyResult		bodyResult	= processBody( context, body, buffer );

		// IF there was a return statement inside our body, we early exit now
		if ( bodyResult.isEarlyExit() ) {
			return bodyResult;
		}

		MailUtil.processMail( buffer, context, attributes, executionState );

		return DEFAULT_RETURN;
	}

}
