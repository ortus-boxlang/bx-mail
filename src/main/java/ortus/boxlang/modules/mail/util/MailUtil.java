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
package ortus.boxlang.modules.mail.util;

import java.io.IOException;
import java.net.IDN;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
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

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.ExpressionInterpreter;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxIOException;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.types.util.BLCollector;
import ortus.boxlang.runtime.types.util.ListUtil;
import ortus.boxlang.runtime.util.FileSystemUtil;

public class MailUtil {

	public static final BoxRuntime	runtime		= BoxRuntime.getInstance();

	static final Key				spoolCache	= MailKeys.mailUnsent;

	static final IStruct			mimeMap		= Struct.of(
	    MailKeys.HTML, "text/html",
	    MailKeys.text, "text/plain",
	    MailKeys.plain, "text/plain"
	);

	/**
	 * This private constructor assures that the current class path will be able to find the correct mappings
	 */
	private MailUtil() {
		MailcapCommandMap mc = ( MailcapCommandMap ) CommandMap.getDefaultCommandMap();
		mc.addMailcap( "text/html;; x-java-content-handler=com.sun.mail.handlers.text_html" );
		mc.addMailcap( "text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml" );
		mc.addMailcap( "text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain" );
		mc.addMailcap( "multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed" );
		mc.addMailcap( "message/rfc822;; x-java-content- handler=com.sun.mail.handlers.message_rfc822" );
	}

	/**
	 * Converter which ensures all unicode email address input is correctly encoded
	 */
	static final IDNEmailAddressConverter IDNConverter = new IDNEmailAddressConverter();

	/**
	 * Processes a mail message from the context and attributes
	 *
	 * @param buffer
	 * @param context
	 * @param attributes
	 * @param executionState
	 */
	public static void processMail( StringBuffer buffer, IBoxContext context, IStruct attributes, IStruct executionState ) {
		IStruct	moduleSettings	= runtime.getModuleService().getModuleSettings( MailKeys._MODULE_NAME );
		String	from			= attributes.getAsString( Key.from );
		String	charset			= attributes.getAsString( Key.charset );
		Boolean	debug			= attributes.getAsBoolean( MailKeys.debug );
		String	mailerid		= attributes.getAsString( MailKeys.mailerid );
		String	mimeAttach		= attributes.getAsString( MailKeys.mimeAttach );
		String	subject			= attributes.getAsString( MailKeys.subject );
		String	messageType		= attributes.getAsString( Key.type );
		Integer	wrapText		= attributes.getAsInteger( MailKeys.wrapText );
		// Encryption attributes
		// Check for any signature settings in the configuration
		MailUtil.applySignatureSettings( attributes );
		Boolean	sign		= attributes.getAsBoolean( MailKeys.sign );
		Boolean	encrypt		= attributes.getAsBoolean( MailKeys.encrypt );

		Array	mailParams	= executionState.getAsArray( MailKeys.mailParams );
		Array	mailParts	= executionState.getAsArray( MailKeys.mailParts );

		Email	message		= ( sign || encrypt || mimeAttach != null || mailParts.size() > 0 || mailParams.size() > 0 )
		    ? new MultiPartEmail()
		    : new SimpleEmail();

		message.setDebug( debug );

		message.setSubject( subject );

		if ( messageType != null ) {
			message.setContentType( messageType.toLowerCase() );
		}

		message.setCharset( charset == null ? moduleSettings.getAsString( MailKeys.defaultEncoding ) : charset );

		if ( messageType == null ) {
			messageType = "text/html";
		} else if ( mimeMap.containsKey( Key.of( messageType ) ) ) {
			messageType = mimeMap.getAsString( Key.of( messageType ) );
		}

		if ( message instanceof SimpleEmail ) {
			message.setContent( wrapText != null ? WordUtils.wrap( buffer.toString(), wrapText ) : buffer.toString(), messageType );
		} else {
			MailUtil.appendMimeContent( ( MultiPartEmail ) message, buffer, attributes, context, mailParams, mailParts );
		}

		try {
			message.setFrom( IDNConverter.toASCII( from ) );
		} catch ( EmailException e ) {
			throw new BoxRuntimeException( "An error occurred while attempting parse a sendable 'from' address.  The message recieved was: " + e.getMessage() );
		}

		if ( mailerid != null ) {
			message.addHeader( "X-Mailer", mailerid );
		}

		MailUtil.setMessageServer( context, attributes, message );

		MailUtil.setMessageRecipients( attributes, message );

		MailUtil.spoolOrSend( message, attributes, context );
	}

	/**
	 * Sets the message recipients
	 *
	 * @param attributes
	 * @param message
	 */
	public static void setMessageRecipients( IStruct attributes, Email message ) {

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
	 * Appends mime content parts to the message
	 *
	 * @param message
	 * @param buffer
	 * @param attributes
	 * @param context
	 * @param mailParams
	 * @param mailParts
	 */
	public static void appendMimeContent(
	    MultiPartEmail message,
	    StringBuffer buffer,
	    IStruct attributes,
	    IBoxContext context,
	    Array mailParams,
	    Array mailParts ) {
		String	mimeAttach	= attributes.getAsString( MailKeys.mimeAttach );
		Boolean	encrypt		= attributes.getAsBoolean( MailKeys.encrypt );
		Boolean	sign		= attributes.getAsBoolean( MailKeys.sign );
		if ( mimeAttach != null ) {
			Path filePath = Path.of( mimeAttach );
			try {
				mailParams.add(
				    Struct.of(
				        MailKeys.disposition, null,
				        Key.file, mimeAttach,
				        MailKeys.fileName, filePath.getFileName().toString(),
				        Key.type, Files.probeContentType( filePath )
				    )
				);
			} catch ( IOException e ) {
				throw new BoxIOException( e );
			}
		}
		// Process the content parts ( e.g. text & html )
		boolean hasContentParts = mailParts.size() > 0
		    &&
		    mailParts.stream().map( StructCaster::cast )
		        .anyMatch(
		            item -> item.getAsString( Key.type ).toLowerCase().contains( "text" ) || item.getAsString( Key.type ).toLowerCase().contains( "html" ) );
		if ( !hasContentParts ) {
			if ( !encrypt && !sign && mailParams.size() == 0 ) {
				message.setContent( buffer.toString() );
				message.setContentType( "text/plain" );
			} else {
				message.setContentType( "multipart/mixed" );
				appendMessagePart( message, buffer.toString(), StringCaster.cast( attributes.getOrDefault( Key.type, "text/plain" ) ),
				    attributes.getAsString( Key.charset ), attributes );
			}
		} else {
			mailParts.stream().map( StructCaster::cast )
			    .forEach( part -> {
				    String mimeType = part.getAsString( Key.type ).toLowerCase();
				    if ( mimeType.contains( "html" ) ) {
					    mimeType = "text/html";
				    } else if ( mimeType.contains( "text" ) ) {
					    mimeType = "text/plain";
				    }
				    appendMessagePart( message, part.get( Key.result ), mimeType, attributes.getAsString( Key.charset ), attributes );
			    } );

		}
		// Process any file attachments
		mailParams.stream().map( StructCaster::cast )
		    .filter( param -> param.get( Key.file ) != null )
		    .forEach( param -> {
			    Path filePath = Path.of( param.getAsString( Key.file ) );
			    try {
				    if ( !attributes.getAsBoolean( MailKeys.sign ) && !attributes.getAsBoolean( MailKeys.encrypt ) ) {
					    // Simple attachment - no encryption or signing
					    EmailAttachment attachment = new EmailAttachment();
					    attachment.setPath( filePath.toAbsolutePath().toString() );
					    attachment.setDisposition( param.get( MailKeys.disposition ) != null ? param.getAsString( MailKeys.disposition ) : Part.ATTACHMENT );
					    if ( param.containsKey( MailKeys.fileName ) ) {
						    attachment.setName( param.getAsString( MailKeys.fileName ) );
					    }
					    if ( param.get( Key.description ) != null ) {
						    attachment.setDescription( param.getAsString( Key.description ) );
					    }
					    message.attach( attachment );
				    } else {
					    // Mime attachments for signed or encrypted content
					    attributes.keySet().stream()
					        .forEach( key -> param.putIfAbsent( key, attributes.get( key ) ) );
					    appendMessagePart(
					        message,
					        FileSystemUtil.read( param.getAsString( Key.file ) ),
					        Files.probeContentType( filePath ),
					        attributes.getAsString( Key.charset ),
					        param
					    );
					    if ( attributes.getAsBoolean( MailKeys.remove ) ) {
						    FileSystemUtil.deleteFile( param.getAsString( Key.file ) );
					    }
				    }

			    } catch ( EmailException e ) {
				    throw new BoxRuntimeException(
				        "An exception occured while attempting to attach the file " + filePath.toAbsolutePath().toString() + ". " + e.getMessage(), e );
			    } catch ( IOException e ) {
				    throw new BoxIOException( e );
			    }

		    } );

		// For signing we need to extract the assembled parts and encapsulate them in a signed MultiPart object with the appropriate headers
		if ( sign ) {
			MimeMultipart signedMultiPart = new MimeMultipart();
			try {
				for ( int i = 0; i < message.getEmailBody().getCount(); i++ ) {
					signedMultiPart.addBodyPart( message.getEmailBody().getBodyPart( i ) );
				}
				MimeBodyPart signedBodyPart = new MimeBodyPart();
				signedBodyPart.setContent( signedMultiPart );
				message.setContent( MailEncryptionUtil.signMessagePart( attributes, signedBodyPart ), signedBodyPart.getContentType() );
			} catch ( MessagingException e ) {
				throw new BoxRuntimeException( "An error occurred while attempting to sign the message: " + e.getMessage(), e );
			}
		}

		// For encryption we also have to extract the assembled parts and encapsulate them in a final encrypted MultiPart object with the appropriate headers
		if ( encrypt ) {
			MimeMultipart encryptedMultiPart = new MimeMultipart();
			try {
				for ( int i = 0; i < message.getEmailBody().getCount(); i++ ) {
					encryptedMultiPart.addBodyPart( message.getEmailBody().getBodyPart( i ) );
				}
				MimeBodyPart encryptedBodyPart = new MimeBodyPart();
				encryptedBodyPart.setContent( encryptedMultiPart );
				MimeMultipart finalMultipart = new MimeMultipart();
				encryptedBodyPart = MailEncryptionUtil.encryptBodyPart( attributes, encryptedBodyPart );
				finalMultipart.addBodyPart( encryptedBodyPart );
				message.setContent( finalMultipart, encryptedBodyPart.getContentType() );
			} catch ( MessagingException e ) {
				throw new BoxRuntimeException( "An error occurred while attempting to encrypt the message: " + e.getMessage(), e );
			}
		}

	}

	/**
	 * Appends an individual message part
	 *
	 * @param message
	 * @param content
	 * @param mimeType
	 * @param charset
	 */
	public static void appendMessagePart( MultiPartEmail message, Object content, String mimeType, String charset, IStruct attributes ) {
		Boolean encrypt = attributes.getAsBoolean( MailKeys.encrypt );
		try {
			MimeMultipart	mimePart	= new MimeMultipart();
			MimeBodyPart	bodyPart	= new MimeBodyPart();
			if ( content instanceof String ) {
				bodyPart.setContent( StringCaster.cast( content ), mimeType + ";charset=" + charset );
			} else {
				bodyPart.setDisposition( attributes.get( MailKeys.disposition ) == null ? Part.INLINE : attributes.getAsString( MailKeys.disposition ) );
				if ( attributes.containsKey( MailKeys.fileName ) ) {
					bodyPart.setFileName( attributes.getAsString( MailKeys.fileName ) );
				} else if ( attributes.containsKey( Key.file ) ) {
					bodyPart.setFileName( Path.of( attributes.getAsString( Key.file ) ).getFileName().toString() );
				}
				bodyPart.setContent( content, mimeType );
			}

			if ( encrypt ) {
				bodyPart = MailEncryptionUtil.encryptBodyPart( attributes, bodyPart );
			}

			mimePart.addBodyPart( bodyPart );
			message.addPart( mimePart );

		} catch ( MessagingException e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to attach content of type " + mimeType + " to the email", e );
		} catch ( EmailException e ) {
			throw new BoxRuntimeException( "An error occurred while attempting to attach content of type " + mimeType + " to the email", e );
		}
	}

	/**
	 * Sets the message server parameters
	 *
	 * @param context
	 * @param attributes
	 * @param message
	 */
	public static void setMessageServer( IBoxContext context, IStruct attributes, Email message ) {

		IStruct	primaryServer	= StructCaster.cast( getMailServers( context, attributes ).get( 0 ) );
		String	username		= primaryServer.getAsString( Key.username );
		Boolean	useSSL			= attributes.getAsBoolean( MailKeys.useSSL );
		Boolean	useTLS			= attributes.getAsBoolean( MailKeys.useTLS );

		message.setPopBeforeSmtp( false );
		message.setHostName( primaryServer.getAsString( Key.server ) );
		message.setSmtpPort( IntegerCaster.cast( primaryServer.get( Key.port ) ) );

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
	 * Parses the mail server attributes and settings
	 *
	 * @param context
	 * @param attributes
	 *
	 * @return
	 */
	public static Array getMailServers( IBoxContext context, IStruct attributes ) {
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
		String IDNAVersion = attributes.getAsString( MailKeys.IDNAVersion );
		if ( server == null ) {
			Array				servers				= null;
			RequestBoxContext	requestContext		= context.getParentOfType( RequestBoxContext.class );
			// check context setings first
			IStruct				requestSettings		= requestContext.getSettings();
			// Explicit module settings
			Array				moduleMailServers	= moduleSettings.getAsArray( MailKeys.mailServers );
			// CFConfig mail servers
			Array				configMailServers	= requestContext.getConfig().getAsStruct( Key.originalConfig ).getAsArray( MailKeys.mailServers );
			// Runtime settings servers
			Array				settingsMailServers	= requestSettings.getAsArray( MailKeys.mailServers );
			if ( moduleMailServers != null && moduleMailServers.size() > 0 ) {
				servers = moduleMailServers;
			} else if ( configMailServers != null && configMailServers.size() > 0 ) {
				servers = configMailServers;
			} else if ( settingsMailServers != null && settingsMailServers.size() > 0 ) {
				servers = settingsMailServers;
			} else {
				throw new BoxRuntimeException( "No mail servers have been defined in any of the available configurations. The message cannot be sent." );
			}
			mailServers = servers.stream().map( StructCaster::cast )
			    .map( serverStruct -> {
				    String smtpSetting	= serverStruct.getAsString( MailKeys.SMTP );
				    String hostSetting	= serverStruct.getAsString( Key.host );
				    String timeoutSetting = serverStruct.getAsString( MailKeys.lifeTimeout );
				    if ( timeoutSetting != null ) {
					    timeoutSetting = serverStruct.getAsString( Key.timeout );
				    }

				    String serverName	= smtpSetting != null
				        ? smtpSetting
				        : ( hostSetting != null
				            ? hostSetting
				            : serverStruct.getAsString( Key.server ) );
				    String serverPort	= serverStruct.getAsString( Key.port );
				    String serverUsername = serverStruct.getAsString( Key.username );
				    String serverPassword = serverStruct.getAsString( Key.password );

				    return Struct.of(
				        Key.server, IDNAVersion != null ? IDN.toASCII( serverName, IDN.USE_STD3_ASCII_RULES ) : IDN.toASCII( serverName ),
				        Key.port, serverPort != null ? IntegerCaster.cast( serverPort ) : 25,
				        Key.username, serverUsername,
				        Key.password, serverPassword,
				        Key.timeout, timeout != null ? timeout
				            : ( timeoutSetting != null
				                ? timeoutSetting
				                : moduleSettings.get( Key.connectionTimeout ) ),
				        MailKeys.SSL, BooleanCaster.cast( serverStruct.get( MailKeys.SSL ) ),
				        MailKeys.TLS, BooleanCaster.cast( serverStruct.get( MailKeys.TLS ) )
				    );
			    } )
			    .collect( BLCollector.toArray() );
		} else {
			mailServers = Array.of(
			    Struct.of(
			        Key.server, IDNAVersion != null ? IDN.toASCII( server, IDN.USE_STD3_ASCII_RULES ) : IDN.toASCII( server ),
			        Key.port, port == null ? 25 : port,
			        Key.username, username,
			        Key.password, password,
			        Key.timeout, timeout == null ? moduleSettings.get( Key.connectionTimeout ) : timeout,
			        MailKeys.SSL, attributes.getAsBoolean( MailKeys.SSL ),
			        MailKeys.TLS, attributes.getAsBoolean( MailKeys.TLS )
			    )
			);
		}

		return mailServers;
	}

	public static void applySignatureSettings( IStruct attributes ) {
		IStruct	moduleSettings	= runtime.getModuleService().getModuleSettings( MailKeys._MODULE_NAME );
		Object	signDirective	= attributes.get( MailKeys.sign );
		if ( signDirective == null && moduleSettings.getAsBoolean( MailKeys.signMesssage ) ) {
			attributes.put( MailKeys.sign, moduleSettings.getAsBoolean( MailKeys.signMesssage ) );
			attributes.put( MailKeys.keystore, moduleSettings.getAsString( MailKeys.signKeystore ) );
			attributes.put( MailKeys.keystorePassword, moduleSettings.getAsString( MailKeys.signKeystorePassword ) );
			attributes.put( MailKeys.keyAlias, moduleSettings.getAsString( MailKeys.signKeyAlias ) );
			attributes.put( MailKeys.keyPassword, moduleSettings.getAsString( MailKeys.signKeyPassword ) );

		} else if ( signDirective == null ) {
			attributes.put( MailKeys.sign, false );
		}

	}

	/**
	 * Spools or sends an email message
	 *
	 * @param message
	 * @param attributes
	 * @param context
	 */
	public static void spoolOrSend( Email message, IStruct attributes, IBoxContext context ) {
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
			    Struct.of(
			        Key.message, message,
			        Key.priority, priority,
			        Key.attributes, attributes
			    )
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
				if ( attributes.getAsBoolean( MailKeys.remove ) && attributes.getAsString( MailKeys.mimeAttach ) != null ) {
					FileSystemUtil.deleteFile( attributes.getAsString( MailKeys.mimeAttach ) );
				}

			} catch ( Throwable e ) {
				throw new BoxRuntimeException( "Message failed to send.", e );
			}
		}

	}
}
