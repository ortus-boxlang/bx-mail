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

import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.mail2.jakarta.Email;
import org.apache.commons.mail2.jakarta.MultiPartEmail;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.events.InterceptorState;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Announces the mail interception points of this module and builds their payloads.
 * <p>
 * The points are:
 * <ul>
 * <li>{@code onMailInvocation}: a message was built and is about to be spooled or sent. Data: {@code id}, {@code spooled}, {@code attributes},
 * {@code summary}.</li>
 * <li>{@code onMailSpooled}: the message was stored in the unsent spool. Data: {@code id}, {@code queueDepth}.</li>
 * <li>{@code onMailDispatch}: a send attempt on one server is about to start. Data: {@code id}, {@code attempt}, {@code failover},
 * {@code fromSpool}, {@code server}.</li>
 * <li>{@code onMailSent}: a server accepted the message. Data: {@code id}, {@code messageId}, {@code attempt}, {@code failover},
 * {@code fromSpool}, {@code server}, {@code ms}.</li>
 * <li>{@code onMailError}: one send attempt failed. Data: {@code id}, {@code attempt}, {@code server}, {@code error}, {@code errorType},
 * {@code willFailover}, {@code fromSpool}, {@code ms}.</li>
 * <li>{@code onMailBounce}: a spooled message could not be sent and was moved to the bounce spool. Data: {@code id}, {@code error},
 * {@code unreadable}.</li>
 * <li>{@code onMailSpoolRun}: one run of the spool task ended. Data: {@code processed}, {@code failures}, {@code remaining}, {@code ms}.</li>
 * </ul>
 * Payloads are plain structs and arrays: no Commons Email object and no password ever leaves the module. They are built only when something
 * listens, and a listener that throws never breaks the mail.
 */
public final class MailEvents {

	public static final Key				ON_INVOCATION		= Key.of( "onMailInvocation" );
	public static final Key				ON_SPOOLED			= Key.of( "onMailSpooled" );
	public static final Key				ON_DISPATCH			= Key.of( "onMailDispatch" );
	public static final Key				ON_SENT				= Key.of( "onMailSent" );
	public static final Key				ON_ERROR			= Key.of( "onMailError" );
	public static final Key				ON_BOUNCE			= Key.of( "onMailBounce" );
	public static final Key				ON_SPOOL_RUN		= Key.of( "onMailSpoolRun" );

	/**
	 * The largest inline part (an image with a content id) that is handed to listeners as bytes. Bigger inline parts and every attachment travel as
	 * name, type and size only.
	 */
	public static final int				MAX_INLINE_BYTES	= 256 * 1024;

	private static final BoxLangLogger	logger				= BoxRuntime.getInstance().getLoggingService().getLogger( "Mail" );

	private MailEvents() {
	}

	/**
	 * Is anything listening to this point? Checked before a payload is built.
	 *
	 * @param point the interception point
	 *
	 * @return true when at least one observer is registered
	 */
	public static boolean listening( Key point ) {
		try {
			var service = BoxRuntime.getInstance().getInterceptorService();
			if ( !service.hasState( point ) ) {
				return false;
			}
			InterceptorState state = service.getState( point );
			return state != null && state.size() > 0;
		} catch ( Throwable t ) {
			return false;
		}
	}

	/**
	 * Announce a point with a payload that is built lazily. Never throws: a failing listener or payload is logged at debug level.
	 *
	 * @param point   the interception point
	 * @param payload builds the data struct, called only when something listens
	 */
	public static void announce( Key point, Supplier<IStruct> payload ) {
		if ( !listening( point ) ) {
			return;
		}
		try {
			BoxRuntime.getInstance().getInterceptorService().announce( point, payload.get() );
		} catch ( Throwable t ) {
			if ( logger.isDebugEnabled() ) {
				logger.debug( "A listener of " + point.getName() + " failed: " + t.getMessage() );
			}
		}
	}

	/**
	 * The attributes of a mail operation that are safe to share: no password, no keystore secret.
	 *
	 * @param attributes the attributes of the {@code bx:mail} call
	 *
	 * @return a new struct with the safe attributes that are set
	 */
	public static IStruct safeAttributes( IStruct attributes ) {
		IStruct out = new Struct();
		for ( String name : List.of( "server", "port", "username", "useSSL", "useTLS", "type", "charset", "priority", "mailerid", "wrapText", "sign",
		    "encrypt", "spoolEnable", "failTo", "timeout" ) ) {
			Object value = attributes.get( Key.of( name ) );
			if ( value != null ) {
				out.put( Key.of( name ), value );
			}
		}
		return out;
	}

	/**
	 * Describes a server without its password.
	 *
	 * @param server a mail server struct as {@code getMailServers} returns it
	 *
	 * @return host, port, ssl, tls and the user name
	 */
	public static IStruct describeServer( IStruct server ) {
		if ( server == null ) {
			return new Struct();
		}
		return Struct.of(
		    "host", firstNonNull( server.get( Key.server ), server.get( MailKeys.SMTP ) ),
		    "port", server.get( Key.port ),
		    "ssl", server.get( MailKeys.SSL ),
		    "tls", server.get( MailKeys.TLS ),
		    "username", server.get( Key.username )
		);
	}

	/**
	 * Describes a message for listeners: addresses, subject, headers and the tree of its parts. Text parts carry their text, an inline part carries
	 * its bytes when it is small, and an attachment carries only its name, type and size.
	 *
	 * @param email the message
	 *
	 * @return a plain struct
	 */
	public static IStruct summarize( Email email ) {
		IStruct out = new Struct();
		try {
			out.put( "subject", email.getSubject() );
			out.put( "contentType", email.getContentType() );
			if ( email.getFromAddress() != null ) {
				out.put( "from", address( email.getFromAddress() ) );
			}
			out.put( "to", addresses( email.getToAddresses() ) );
			out.put( "cc", addresses( email.getCcAddresses() ) );
			out.put( "bcc", addresses( email.getBccAddresses() ) );
			out.put( "replyTo", addresses( email.getReplyToAddresses() ) );
			out.put( "headers", new Struct( email.getHeaders() ) );
			Array parts = new Array();
			if ( email instanceof MultiPartEmail multi && multi.getEmailBody() != null ) {
				MimeMultipart body = multi.getEmailBody();
				out.put( "multipart", subtype( body ) );
				for ( int i = 0; i < body.getCount(); i++ ) {
					parts.add( describePart( body.getBodyPart( i ) ) );
				}
			} else {
				Object content = email.getContent();
				parts.add( Struct.of(
				    "type", "text",
				    "contentType", email.getContentType(),
				    "size", content == null ? 0 : content.toString().length(),
				    "content", content == null ? "" : content.toString()
				) );
			}
			out.put( "parts", parts );
		} catch ( Throwable t ) {
			out.put( "error", "The message could not be described: " + t.getMessage() );
		}
		return out;
	}

	private static IStruct describePart( BodyPart bodyPart ) throws Exception {
		IStruct	data	= new Struct();
		String	type	= bodyPart.getContentType();
		data.put( "contentType", type );
		String disposition = bodyPart.getDisposition();
		if ( disposition != null ) {
			data.put( "disposition", disposition );
		}
		String fileName = bodyPart.getFileName();
		if ( fileName != null ) {
			data.put( "fileName", fileName );
		}
		if ( bodyPart instanceof MimeBodyPart mime && mime.getContentID() != null ) {
			data.put( "contentID", mime.getContentID() );
		}
		Object content = bodyPart.getContent();
		if ( content instanceof Multipart nested ) {
			data.put( "type", "multipart" );
			data.put( "multipart", subtype( nested ) );
			Array children = new Array();
			for ( int i = 0; i < nested.getCount(); i++ ) {
				children.add( describePart( nested.getBodyPart( i ) ) );
			}
			data.put( "parts", children );
		} else if ( content instanceof String text && fileName == null && !Part.ATTACHMENT.equalsIgnoreCase( disposition ) ) {
			data.put( "type", "text" );
			data.put( "size", text.length() );
			data.put( "content", text );
		} else {
			data.put( "type", "binary" );
			boolean	inline	= Part.INLINE.equalsIgnoreCase( disposition ) || data.containsKey( Key.of( "contentID" ) );
			int		size	= bodyPart.getSize();
			if ( size < 0 || ( inline && size <= MAX_INLINE_BYTES ) ) {
				// The size is unknown until the data is read, and a small inline part is handed over
				try ( InputStream in = bodyPart.getInputStream() ) {
					byte[] bytes = in.readNBytes( MAX_INLINE_BYTES + 1 );
					if ( bytes.length > MAX_INLINE_BYTES ) {
						size = bytes.length + in.available();
					} else {
						size = bytes.length;
						if ( inline ) {
							data.put( "bytes", bytes );
						}
					}
				}
			}
			data.put( "size", size );
		}
		return data;
	}

	private static String subtype( Multipart multipart ) {
		String type = multipart.getContentType();
		if ( type == null ) {
			return "mixed";
		}
		int	slash	= type.indexOf( '/' );
		int	end		= type.indexOf( ';' );
		return ( slash < 0 ? type : type.substring( slash + 1, end < 0 ? type.length() : end ) ).trim().toLowerCase();
	}

	private static Array addresses( List<InternetAddress> list ) {
		Array out = new Array();
		if ( list != null ) {
			for ( InternetAddress a : list ) {
				out.add( address( a ) );
			}
		}
		return out;
	}

	private static IStruct address( InternetAddress a ) {
		return Struct.of( "email", a.getAddress(), "name", a.getPersonal() );
	}

	private static Object firstNonNull( Object a, Object b ) {
		return a != null ? a : b;
	}

}
