### Simple Email Example ( Script syntax )

```javascript
bx:mail
    from="jclausen@ortussolutions.com"
    to="jclausen@ortussolutions.com"
    subject="Hello from BoxLang Mail!"
{
    writeOutput( "Hello world!" );
}
```

### Email with a single file attachment ( Templating syntax )

```javascript
<bx:mail
    from="jclausen@ortussolutions.com"
    to="jclausen@ortussolutions.com"
    subject="File For You"
    mimeAttach="/path/to/my/file.pdf"
>
Here's a PDF for you!
</bx:mail>
```

### MultiPart with text and html parts, with an attachment ( Templating syntax )

```javascript
<bx:mail
	from="jclausen@ortussolutions.com"
	to="jclausen@ortussolutions.com"
	subject="Mail In Parts"
>

	<bx:mailpart type="text">
	Hello mail!
	</bx:mailpart>

	<bx:mailpart type="html">
	<h1>Hello mail!</h1>
	</bx:mailpart>

	<bx:mailparam file="/path/to/my/file.pdf" fileName="PDFForYou.pdf" type="application/x-pdf" />

</bx:mail>
```
