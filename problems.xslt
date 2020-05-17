<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="text" encoding="iso-8859-1"/>
  <xsl:template match="problem">
    <xsl:text>::</xsl:text>
    <xsl:text>warning </xsl:text>
    <xsl:text>file=</xsl:text>
    <xsl:value-of select="file"/>
    <xsl:text>,line=</xsl:text>
    <xsl:value-of select="line"/>
    <xsl:text>::</xsl:text>
    <xsl:value-of select="description" disable-output-escaping="yes"/>
  </xsl:template>
</xsl:stylesheet>