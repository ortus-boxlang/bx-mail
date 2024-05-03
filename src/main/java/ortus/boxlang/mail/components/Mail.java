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

import java.net.IDN;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.commons.mail.SimpleEmail;
import org.apache.commons.mail.util.IDNEmailAddressConverter;
import org.apache.commons.text.WordUtils;

import ortus.boxlang.mail.util.MailKeys;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.BoxComponent;
import ortus.boxlang.runtime.components.Component;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.ExpressionInterpreter;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.util.ListUtil;
import ortus.boxlang.runtime.validation.Validator;

@BoxComponent( allowsBody = true, requiresBody = true )
public class Mail extends Component {

	static final Key						spoolCache		= MailKeys.mailUnsent;

	static final IStruct					mimeMap			= Struct.of(
	    MailKeys.HTML, "text/html",
	    MailKeys.text, "text/plain",
	    MailKeys.plain, "text/plain"
	);
	/*
	 * Converter which ensures all unicode email address input is correctly encoded
	 */
	static final IDNEmailAddressConverter	IDNConverter	= new IDNEmailAddressConverter();

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
		    // Disabling spoolEnable by default for now until can fix the Scheduler not being picked up
		    new Attribute( MailKeys.spoolEnable, "boolean", false ), // "yes|no"
		    new Attribute( Key.timeout, "string" ), // "number of seconds"
		    new Attribute( Key.type, "string" ), // "mime type"
		    new Attribute( Key.username, "string" ), // "SMTP user ID"
		    new Attribute( MailKeys.useSSL, "string" ), // "yes|no"
		    new Attribute( MailKeys.useTLS, "string" ), // "yes|no"
		    new Attribute( MailKeys.wrapText, "string" ), // "column number"
		    new Attribute( MailKeys.sign, "boolean", false ), // "true|false"
		    new Attribute( MailKeys.keystore, "string" ), // "location of keystore"
		    new Attribute( MailKeys.keystorePassword, "string" ), // "password of keystore"
		    new Attribute( MailKeys.keyAlias, "string" ), // "alias of key"
		    new Attribute( MailKeys.keyPassword, "string" ), // "password for private key"
		    new Attribute( MailKeys.encrypt, "boolean", false ), // "true|false"
		    new Attribute( MailKeys.recipientCert, "string" ), // <path to the public key cert>
		    new Attribute( MailKeys.encryptionAlgorithm, "string" ), // "DES_EDE3_CBC, RC2_CBC, AES128_CBC, AES192_CBC, AES256_CBC"
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
	 * An example component that says hello
	 *
	 * @param context        The context in which the Component is being invoked
	 * @param attributes     The attributes to the Component
	 * @param body           The body of the Component
	 * @param executionState The execution state of the Component
	 *
	 * @attribute.from "e-mail address"
	 *
	 * @attribute.to "comma-delimited list"
	 *
	 * @attribute.charset "character encoding"
	 *
	 * @attribute.debug "yes|no"
	 *
	 * @attribute.mailerid "header id"
	 *
	 * @attribute.mimeAttach "path of file to attach"
	 *
	 * @attribute.subject "string"
	 *
	 * @attribute.type "mime type"
	 *
	 * @attribute.wrapText "column number"
	 *
	 * @attribute.sign "true|false"
	 *
	 * @attribute.encrypt "true|false"
	 *
	 * @attribute.query "query name"
	 *
	 * @attribute.group "query column"
	 *
	 * @attribute.groupCaseSensitive "yes|no"
	 *
	 * @attribute.startRow "query row number"
	 *
	 * @attribute.maxRows "integer"
	 *
	 * @return An empty body result is returned
	 *
	 */
	public BodyResult _invoke( IBoxContext context, IStruct attributes, ComponentBody body, IStruct executionState ) {

		IStruct	moduleSettings		= runtime.getModuleService().getModuleSettings( MailKeys._MODULE_NAME );
		String	from				= attributes.getAsString( Key.from );
		String	charset				= attributes.getAsString( Key.charset );
		Boolean	debug				= attributes.getAsBoolean( MailKeys.debug );
		String	mailerid			= attributes.getAsString( MailKeys.mailerid );
		String	mimeAttach			= attributes.getAsString( MailKeys.mimeAttach );
		String	subject				= attributes.getAsString( MailKeys.subject );
		String	messageType			= attributes.getAsString( Key.type );
		Integer	wrapText			= attributes.getAsInteger( MailKeys.wrapText );
		// Encryption attributes
		Boolean	sign				= attributes.getAsBoolean( MailKeys.sign );
		Boolean	encrypt				= attributes.getAsBoolean( MailKeys.encrypt );
		// Query-specific attributes
		Object	query				= attributes.get( Key.query );
		String	group				= attributes.getAsString( Key.group );
		Boolean	groupCaseSensitive	= attributes.getAsBoolean( MailKeys.groupCaseSensitive );
		Integer	startRow			= attributes.getAsInteger( Key.startRow );
		Integer	maxRows				= attributes.getAsInteger( Key.maxRows );

		executionState.put( MailKeys.mailParams, new Array() );
		executionState.put( MailKeys.mailParts, new Array() );

		StringBuffer	buffer		= new StringBuffer();

		BodyResult		bodyResult	= processBody( context, body, buffer );
		// IF there was a return statement inside our body, we early exit now
		if ( bodyResult.isEarlyExit() ) {
			return bodyResult;
		}

		Array	mailParams	= executionState.getAsArray( MailKeys.mailParams );
		Array	mailParts	= executionState.getAsArray( MailKeys.mailParts );

		Email	message		= ( mimeAttach != null || mailParts.size() > 0 || mailParams.size() > 0 )
		    ? new MultiPartEmail()
		    : new SimpleEmail();

		message.setDebug( debug );

		message.setSubject( subject );
		message.setCharset( charset == null ? moduleSettings.getAsString( MailKeys.defaultEncoding ) : charset );

		if ( messageType == null ) {
			messageType = "text/html";
		} else if ( mimeMap.containsKey( Key.of( messageType ) ) ) {
			messageType = mimeMap.getAsString( Key.of( messageType ) );
		}

		if ( message instanceof SimpleEmail ) {
			message.setContent( wrapText != null ? WordUtils.wrap( buffer.toString(), wrapText ) : buffer.toString(), messageType );
		} else {
			if ( mailParts.size() == 0 ) {
				message.setContent( wrapText != null ? WordUtils.wrap( buffer.toString(), wrapText ) : buffer.toString(), messageType );
			}
			appendMimeContent( ( MultiPartEmail ) message, buffer, attributes, context, mailParams, mailParts );
		}

		try {
			message.setFrom( IDNConverter.toASCII( from ) );
		} catch ( EmailException e ) {
			throw new BoxRuntimeException( "An error occurred while attempting parse a sendable 'from' address.  The message recieved was: " + e.getMessage() );
		}

		if ( mailerid != null ) {
			message.addHeader( "X-Mailer", mailerid );
		}

		setMessageServer( context, attributes, message );

		setMessageRecipients( attributes, message );

		if ( sign || encrypt ) {
			signAndEncrypt( attributes, message );
		}

		spoolOrSend( message, attributes, context );

		return DEFAULT_RETURN;
	}

	/**
	 * Sets the message recipients
	 *
	 * @param attributes
	 * @param message
	 */
	void setMessageRecipients( IStruct attributes, Email message ) {

		String	to		= attributes.getAsString( Key.to );
		String	bcc		= attributes.getAsString( MailKeys.bcc );
		String	cc		= attributes.getAsString( MailKeys.cc );
		String	replyTo	= attributes.getAsString( MailKeys.replyTo );
		String	failTo	= attributes.getAsString( MailKeys.failTo );

		message.setBounceAddress( failTo != null ? IDNConverter.toASCII( failTo ) : IDNConverter.toASCII( attributes.getAsString( Key.from ) ) );

		ListUtil.asList( to, ", ", false, false )
		    .stream()
		    .forEach( address -> {
			    try {
				    message.addTo( IDNConverter.toASCII( StringCaster.cast( address ) ) );
			    } catch ( EmailException e ) {
				    throw new BoxRuntimeException(
				        "An error occurred while attempting parse a sendable 'to' address.  The message recieved was: " + e.getMessage() );
			    }
		    } );
		if ( cc != null ) {
			ListUtil.asList( to, ", ", false, false )
			    .stream()
			    .forEach( address -> {
				    try {
					    message.addCc( IDNConverter.toASCII( StringCaster.cast( address ) ) );
				    } catch ( EmailException e ) {
					    throw new BoxRuntimeException(
					        "An error occurred while attempting parse a sendable 'cc' address.  The message recieved was: " + e.getMessage() );
				    }
			    } );
		}
		if ( bcc != null ) {
			ListUtil.asList( to, ", ", false, false )
			    .stream()
			    .forEach( address -> {
				    try {
					    message.addBcc( IDNConverter.toASCII( StringCaster.cast( address ) ) );
				    } catch ( EmailException e ) {
					    throw new BoxRuntimeException(
					        "An error occurred while attempting parse a sendable 'bcc' address.  The message recieved was: " + e.getMessage() );
				    }
			    } );
		}

		if ( replyTo != null ) {
			ListUtil.asList( to, ", ", false, false )
			    .stream()
			    .forEach( address -> {
				    try {
					    message.addReplyTo( IDNConverter.toASCII( StringCaster.cast( address ) ) );
				    } catch ( EmailException e ) {
					    throw new BoxRuntimeException(
					        "An error occurred while attempting parse a sendable 'replyTo' address.  The message recieved was: " + e.getMessage() );
				    }
			    } );
		}

	}

	/**
	 * Sets the message server parameters
	 *
	 * @param context
	 * @param attributes
	 * @param message
	 */
	void setMessageServer( IBoxContext context, IStruct attributes, Email message ) {

		IStruct	primaryServer	= StructCaster.cast( getMailServers( context, attributes ).get( 0 ) );
		String	username		= primaryServer.getAsString( Key.username );
		Boolean	useSSL			= attributes.getAsBoolean( MailKeys.useSSL );
		Boolean	useTLS			= attributes.getAsBoolean( MailKeys.useTLS );

		message.setPopBeforeSmtp( false );
		message.setHostName( primaryServer.getAsString( Key.server ) );
		message.setSmtpPort( primaryServer.getAsInteger( Key.port ) );

		if ( username != null && username.length() > 0 ) {
			String password = primaryServer.getAsString( Key.password );
			if ( password == null ) {
				throw new BoxRuntimeException( "A password must be provided when specifying a username for SMTP authentication" );
			}
			message.setAuthentication( username, password );
		}

		if ( useTLS == null && primaryServer.getAsBoolean( MailKeys.TLS ) != null ) {
			useTLS = primaryServer.getAsBoolean( MailKeys.TLS );
		}

		if ( useSSL == null && primaryServer.getAsBoolean( MailKeys.SSL ) != null ) {
			useSSL = primaryServer.getAsBoolean( MailKeys.SSL );
		}

		if ( useTLS != null ) {
			message.setStartTLSRequired( useTLS );
		}

		if ( useSSL != null ) {
			message.setSSLOnConnect( useSSL );
		}

	}

	/**
	 * Appends mime content parts to the message
	 *
	 * @param message
	 * @param buffer
	 * @param attributes
	 * @param context
	 * @param mailParams
	 * @param mailParts
	 */
	private void appendMimeContent( MultiPartEmail message, StringBuffer buffer, IStruct attributes, IBoxContext context, Array mailParams, Array mailParts ) {
		String	mimeAttach	= attributes.getAsString( MailKeys.mimeAttach );
		Boolean	remove		= attributes.getAsBoolean( MailKeys.remove );
		if ( mimeAttach != null ) {
			try {
				message.attach( Path.of( mimeAttach ), remove ? StandardOpenOption.DELETE_ON_CLOSE : StandardOpenOption.READ );
			} catch ( EmailException e ) {
				throw new BoxRuntimeException(
				    "An error occurred while attempting to attach the file " + mimeAttach,
				    e
				);
			}
		}
		boolean hasContentParts = mailParts.size() > 0
		    &&
		    mailParts.stream().map( StructCaster::cast )
		        .anyMatch(
		            item -> item.getAsString( Key.type ).toLowerCase().contains( "text" ) || item.getAsString( Key.type ).toLowerCase().contains( "html" ) );
		if ( !hasContentParts ) {
			message.setContent( buffer.toString() );
		} else {
			mailParts.stream().map( StructCaster::cast )
			    .forEach( part -> {
				    String mimeType = part.getAsString( Key.type ).toLowerCase();
				    if ( mimeType.contains( "html" ) ) {
					    mimeType = "text/html";
				    } else if ( mimeType.contains( "text" ) ) {
					    mimeType = "text/plain";
				    }
				    appendMessagePart( message, part.get( Key.result ), mimeType, attributes.getAsString( Key.charset ) );
			    } );

		}
		// File attachment
		mailParams.stream().map( StructCaster::cast )
		    .filter( param -> param.get( Key.file ) != null )
		    .forEach( param -> {
			    Path filePath = Path.of( param.getAsString( Key.file ) );
			    try {
				    if ( param.get( MailKeys.disposition ) == null ) {
					    message.attach( filePath, remove ? StandardOpenOption.DELETE_ON_CLOSE : StandardOpenOption.READ );
				    } else {
					    EmailAttachment attachment = new EmailAttachment();
					    attachment.setPath( filePath.toAbsolutePath().toString() );
					    attachment.setDisposition( param.getAsString( MailKeys.disposition ) );
					    if ( param.containsKey( MailKeys.fileName ) ) {
						    attachment.setName( param.getAsString( MailKeys.fileName ) );
					    }
					    if ( param.get( Key.description ) != null ) {
						    attachment.setDescription( param.getAsString( Key.description ) );
					    }
					    message.attach( attachment );
				    }
			    } catch ( EmailException e ) {
				    throw new BoxRuntimeException(
				        "An error occurred while attempting to attach the file " + filePath.toAbsolutePath().toString(),
				        e
				    );
			    }

		    } );

	}

	/**
	 * Appends an individual message part
	 *
	 * @param message
	 * @param content
	 * @param mimeType
	 * @param charset
	 */
	private void appendMessagePart( MultiPartEmail message, Object content, String mimeType, String charset ) {
		try {
			if ( content instanceof String ) {
				message.addPart( StringCaster.cast( content ), mimeType + ";charset=" + charset );
			} else {
				MimeMultipart	mimePart	= new MimeMultipart();
				BodyPart		bodyPart	= new MimeBodyPart();
				bodyPart.setDisposition( Part.INLINE );
				bodyPart.setContent( content, mimeType );
				mimePart.addBodyPart( bodyPart );
				message.addPart( mimePart );
			}
		} catch ( MessagingException e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to attach content of type " + mimeType + " to the email", e );
		} catch ( EmailException e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to attach content of type " + mimeType + " to the email", e );
		}
	}

	/**
	 * Parses the mail server attributes and settings
	 *
	 * @param context
	 * @param attributes
	 *
	 * @return
	 */
	private Array getMailServers( IBoxContext context, IStruct attributes ) {
		String	server			= attributes.getAsString( Key.server );
		String	username		= attributes.getAsString( Key.username );
		String	password		= attributes.getAsString( Key.password );
		Integer	port			= attributes.getAsInteger( Key.port );
		String	timeout			= attributes.getAsString( Key.timeout );
		IStruct	moduleSettings	= runtime.getModuleService().getModuleSettings( MailKeys._MODULE_NAME );

		Array	mailServers		= null;
		if ( attributes.containsKey( MailKeys.SMTP ) ) {
			server = attributes.getAsString( MailKeys.SMTP );
		}
		if ( server == null ) {
			// check context setings first
			IStruct requestSettings = context.getParentOfType( RequestBoxContext.class ).getSettings();
			if ( requestSettings.containsKey( MailKeys.mailServers ) ) {
				mailServers = requestSettings.getAsArray( MailKeys.mailServers );
			} else if ( moduleSettings.getAsArray( MailKeys.mailServers ).size() > 0 ) {
				mailServers = moduleSettings.getAsArray( MailKeys.mailServers );
			} else {
				throw new BoxRuntimeException( "No mail servers have been defined in any of the available configurations. The message cannot be sent." );
			}
		} else {
			String IDNAVersion = attributes.getAsString( MailKeys.IDNAVersion );
			mailServers = Array.of(
			    Struct.of(
			        Key.server, IDNAVersion != null ? IDN.toASCII( server, IDN.USE_STD3_ASCII_RULES ) : IDN.toASCII( server ),
			        Key.port, port == null ? 25 : port,
			        Key.username, username,
			        Key.password, password,
			        Key.timeout, timeout == null ? moduleSettings.get( Key.connectionTimeout ) : timeout
			    )
			);
		}

		return mailServers;
	}

	/**
	 * Spools or sends the email message
	 *
	 * @param message
	 * @param attributes
	 * @param context
	 */
	void spoolOrSend( Email message, IStruct attributes, IBoxContext context ) {
		IStruct	moduleSettings	= runtime.getModuleService().getModuleSettings( MailKeys._MODULE_NAME );
		Boolean	spoolEnable		= attributes.getAsBoolean( MailKeys.spoolEnable );
		String	priority		= attributes.getAsString( Key.priority );
		String	messageId		= null;
		if ( spoolEnable == null ) {
			spoolEnable = moduleSettings.getAsBoolean( MailKeys.spoolEnable );
		}

		if ( attributes.get( MailKeys.messageVariable ) != null ) {
			ExpressionInterpreter.setVariable(
			    context,
			    attributes.getAsString( MailKeys.messageVariable ),
			    message
			);
		}

		if ( spoolEnable ) {
			messageId = UUID.randomUUID().toString();
			runtime.getCacheService().getCache( spoolCache ).set(
			    messageId,
			    message
			);
		} else {
			try {
				messageId = message.send();

				if ( attributes.get( MailKeys.messageIdentifier ) != null ) {
					ExpressionInterpreter.setVariable(
					    context,
					    attributes.getAsString( MailKeys.messageIdentifier ),
					    messageId
					);
				}

			} catch ( Throwable e ) {
				throw new BoxRuntimeException( "Message failed to send.", e );
			}
		}

	}

	/**
	 * Sign and/or encrypt the email
	 *
	 * @param attributes
	 * @param message
	 */
	private void signAndEncrypt( IStruct attributes, Email message ) {
		// Encryption attributes
		Boolean	sign				= attributes.getAsBoolean( MailKeys.sign );
		String	keystore			= attributes.getAsString( MailKeys.keystore );
		String	keystorePassword	= attributes.getAsString( MailKeys.keystorePassword );
		String	keyAlias			= attributes.getAsString( MailKeys.keyAlias );
		String	keyPassword			= attributes.getAsString( MailKeys.keyPassword );
		Boolean	encrypt				= attributes.getAsBoolean( MailKeys.encrypt );
		String	recipientCert		= attributes.getAsString( MailKeys.recipientCert );
		String	encryptionAlgorithm	= attributes.getAsString( MailKeys.encryptionAlgorithm );
		// TODO
		throw new BoxRuntimeException( "Email signatures and encryption are not yet implemented." );
	}

}
