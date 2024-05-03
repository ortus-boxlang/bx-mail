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
package com.ortussolutions.boxlang.mail.util;

import ortus.boxlang.runtime.scopes.Key;

/**
 * Represents a case-insenstive key, while retaining the original case too.
 * Implements the Serializable interface in case duplication is requested within
 * a native HashMap or ArrayList
 */
public class MailKeys {

	public static final Key	_MODULE_NAME		= Key.of( "mail" );

	public static final Key	bcc					= Key.of( "bcc" );
	public static final Key	bounceDirectory		= Key.of( "bounceDirectory" );
	public static final Key	bounceTimeout		= Key.of( "bounceTimeout" );
	public static final Key	cc					= Key.of( "cc" );
	public static final Key	contentID			= Key.of( "contentID" );
	public static final Key	debug				= Key.of( "debug" );
	public static final Key	defaultEncoding		= Key.of( "defaultEncoding" );
	public static final Key	disposition			= Key.of( "disposition" );
	public static final Key	encrypt				= Key.of( "encrypt" );
	public static final Key	encryptionAlgorithm	= Key.of( "encryptionAlgorithm" );
	public static final Key	failures			= Key.of( "failures" );
	public static final Key	failTo				= Key.of( "failTo" );
	public static final Key	fileName			= Key.of( "fileName" );
	public static final Key	fileSystemStore		= Key.of( "fileSystemStore" );
	public static final Key	groupCaseSensitive	= Key.of( "groupCaseSensitive" );
	public static final Key	HTML				= Key.of( "HTML" );
	public static final Key	IDNAVersion			= Key.of( "iDNAVersion" );
	public static final Key	keyAlias			= Key.of( "keyAlias" );
	public static final Key	keyPassword			= Key.of( "keyPassword" );
	public static final Key	keystore			= Key.of( "keystore" );
	public static final Key	keystorePassword	= Key.of( "keystorePassword" );
	public static final Key	logEnabled			= Key.of( "logEnabled" );
	public static final Key	Mail				= Key.of( "Mail" );
	public static final Key	mailerid			= Key.of( "mailerid" );
	public static final Key	mailBounced			= Key.of( "mailBounced" );
	public static final Key	mailParams			= Key.of( "mailParams" );
	public static final Key	mailServers			= Key.of( "mailServers" );
	public static final Key	mailUnsent			= Key.of( "mailUnsent" );
	public static final Key	mailParts			= Key.of( "mailParts" );
	public static final Key	messages			= Key.of( "messages" );
	public static final Key	messageVariable		= Key.of( "messageVariable" );
	public static final Key	messageIdentifier	= Key.of( "messageIdentifier" );
	public static final Key	mimeAttach			= Key.of( "mimeAttach" );
	public static final Key	recipientCert		= Key.of( "recipientCert" );
	public static final Key	plain				= Key.of( "plain" );
	public static final Key	processed			= Key.of( "processed" );
	public static final Key	remove				= Key.of( "remove" );
	public static final Key	replyTo				= Key.of( "replyTo" );
	public static final Key	sign				= Key.of( "sign" );
	public static final Key	SMTP				= Key.of( "SMTP" );
	public static final Key	spoolEnable			= Key.of( "spoolEnable" );
	public static final Key	spoolInterval		= Key.of( "spoolInterval" );
	public static final Key	spoolDirectory		= Key.of( "spoolDirectory" );
	public static final Key	spoolTimeout		= Key.of( "spoolTimeout" );
	public static final Key	success				= Key.of( "success" );
	public static final Key	subject				= Key.of( "subject" );
	public static final Key	SSL					= Key.of( "SSL" );
	public static final Key	text				= Key.of( "text" );
	public static final Key	TLS					= Key.of( "TLS" );
	public static final Key	useSSL				= Key.of( "useSSL" );
	public static final Key	useTLS				= Key.of( "useTLS" );
	public static final Key	wrapText			= Key.of( "wrapText" );

}
