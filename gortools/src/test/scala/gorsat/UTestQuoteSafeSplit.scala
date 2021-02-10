/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2019 WuXi NextCode Inc.
 *  All Rights Reserved.
 *
 *  GORpipe is free software: you can redistribute it and/or modify
 *  it under the terms of the AFFERO GNU General Public License as published by
 *  the Free Software Foundation.
 *
 *  GORpipe is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
 *  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 *  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
 *  the AFFERO GNU General Public License for the complete license terms.
 *
 *  You should have received a copy of the AFFERO GNU General Public License
 *  along with GORpipe.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
 *
 *  END_COPYRIGHT
 */

package gorsat

import Commands.CommandParseUtilities._
import Commands._
import org.gorpipe.exceptions.GorParsingException
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.slf4j.{Logger, LoggerFactory}

@RunWith(classOf[JUnitRunner])
class UTestQuoteSafeSplit extends FunSuite{

  private val log: Logger = LoggerFactory.getLogger(this.getClass)

  test("Basic split with no nestation") {
    val data = "gor foo bar -h -a"
    val result =  quoteSafeSplit(data, ' ')

    assert(result.length == 5)
    assert("gor" == result(0))
    assert("foo" == result(1))
    assert("bar" == result(2))
    assert("-h" == result(3))
    assert("-a" == result(4))
  }

  test("Basic split with nested query") {
    val data = "gor foo bar -h -a <(nor foo1 bar1)"
    val result =  quoteSafeSplit(data, ' ')

    assert(result.length == 6)
    assert("gor" == result(0))
    assert("foo" == result(1))
    assert("bar" == result(2))
    assert("-h" == result(3))
    assert("-a" == result(4))
    assert("<(nor foo1 bar1)" == result(5))
  }

  test("Basic split with nested query 2") {
    val data = "gor foo bar -h -a <(nor foo1 bar1) gar far"
    val result =  quoteSafeSplit(data, ' ')

    assert(result.length == 8)
    assert("gor" == result(0))
    assert("foo" == result(1))
    assert("bar" == result(2))
    assert("-h" == result(3))
    assert("-a" == result(4))
    assert("<(nor foo1 bar1)" == result(5))
    assert("gar" == result(6))
    assert("far" == result(7))

  }

  test("Basic split with nested query with quotes") {
    val data = "gor foo \"bar -h -a\" <(nor foo1 bar1) gar far"
    val result =  quoteSafeSplit(data, ' ')

    assert(result.length == 6)
    assert("gor" == result(0))
    assert("foo" == result(1))
    assert("\"bar -h -a\"" == result(2))
    assert("<(nor foo1 bar1)" == result(3))
    assert("gar" == result(4))
    assert("far" == result(5))
  }

  test("Basic split with nested query with quotes aligned") {
    val data = "\"gor foo\" \"bar -h -a\" <(nor foo1 bar1) gar far"
    val result =  quoteSafeSplit(data, ' ')

    assert(result.length == 5)
    assert("\"gor foo\"" == result(0))
    assert("\"bar -h -a\"" == result(1))
    assert("<(nor foo1 bar1)" == result(2))
    assert("gar" == result(3))
    assert("far" == result(4))
  }

  test("Basic split with nested query with brakets") {
    val data = "gor foo <(nor foo1() bar1)"
    val result =  quoteSafeSplit(data, ' ')

    assert(result.length == 3)
    assert("gor" == result(0))
    assert("foo" == result(1))
    assert("<(nor foo1() bar1)" == result(2))
  }

  test("Basic split with nested query with nested query") {
    val data = "gor foo <(nor foo1() bar1 <(git fit))"
    val result =  quoteSafeSplit(data, ' ')

    assert(result.length == 3)
    assert("gor" == result(0))
    assert("foo" == result(1))
    assert("<(nor foo1() bar1 <(git fit))" == result(2))
  }

  test("Basic split with quotes over nested query with nested query") {
    val data = "gor \"foo <(nor foo1() bar1 <(git fit))\""
    val result =  quoteSafeSplit(data, ' ')

    assert(result.length == 2)
    assert("gor" == result(0))
    assert("\"foo <(nor foo1() bar1 <(git fit))\"" == result(1))
  }

  test("Basic split nested query with quotes") {
    val data = "gor foo <(nor \"foo1() bar1\")"
    val result =  quoteSafeSplit(data, ' ')

    assert(result.length == 3)
    assert("gor" == result(0))
    assert("foo" == result(1))
    assert("<(nor \"foo1() bar1\")" == result(2))
  }

  test("Split after nested query") {
    val data = "gor foo <(nor foo1() bar1)friggum"
    val result =  quoteSafeSplit(data, ' ')

    assert(result.length == 3)
    assert("gor" == result(0))
    assert("foo" == result(1))
    assert("<(nor foo1() bar1)friggum" == result(2))
  }

  test("Validate valid nested query") {
    val data = "gor foo <(nor foo1() bar1)"
    val result =  quoteSafeSplit(data, ' ')
    val nestedQuery = CommandParseUtilities.parseNestedCommand(result(2))
    assert(nestedQuery.startsWith("nor"))
  }

  test("Validate multiple valid nested queries") {
    val data = "gor foo <(nor foo1() bar1) <(gor foo1() bar1) <(for foo1() bar1)"
    val result =  quoteSafeSplit(data, ' ')
    val nestedQuery1 = CommandParseUtilities.parseNestedCommand(result(2))
    val nestedQuery2 = CommandParseUtilities.parseNestedCommand(result(3))
    val nestedQuery3 = CommandParseUtilities.parseNestedCommand(result(4))
    assert(nestedQuery1.startsWith("nor"))
    assert(nestedQuery2.startsWith("gor"))
    assert(nestedQuery3.startsWith("for"))
  }

  test("Validate invalid nested query") {
    val data = "gor foo <(nor foo1() bar1)friggum"
    val result =  quoteSafeSplit(data, ' ')
    val thrown = intercept[GorParsingException] {
      CommandParseUtilities.parseNestedCommand(result(2))
    }
    assert(thrown != null)
  }

  test("Parse curly brackets") {
    val data = "gor foo {nor foo1() bar1}"
    val result =  quoteSafeSplit(data, ' ')
    assert(result.length == 3)
    assert(result(0) == "gor")
    assert(result(2) == "{nor foo1() bar1}")
  }

  test("Parse curly brackets with non matching parenthesis") {
    val data = "gor foo {nor foo1() bar1)"
    val thrown = intercept[GorParsingException](quoteSafeSplit(data, ' '))
    assert(thrown.getMessage.startsWith("Non matching"))
  }

  test("Parse curly brackets with open ended parenthesis") {
    val data = "gor foo (nor foo1"
    val thrown = intercept[GorParsingException](quoteSafeSplit(data, ' '))
    assert(thrown.getMessage.startsWith("Non matching"))
  }

  test("Parse curly brackets with closed ended parenthesis") {
    val data = "gor foo nor) foo1"
    val thrown = intercept[GorParsingException](quoteSafeSplit(data, ' '))
    assert(thrown.getMessage.startsWith("Non matching"))
  }

  test("Parse curly brackets with open ended parenthesis inside curly brackets") {
    val data = "gor {foo (nor foo1}"
    val thrown = intercept[GorParsingException](quoteSafeSplit(data, ' '))
    assert(thrown.getMessage.startsWith("Non matching"))
  }

  test("Parse curly brackets with closed ended parenthesis inside curly brackets") {
    val data = "gor {foo nor) foo1}"
    val thrown = intercept[GorParsingException](quoteSafeSplit(data, ' '))
    assert(thrown.getMessage.startsWith("Non matching"))
  }

  test("Split on pipes with blocks") {
    val data = "gor foo|bar car| goo {gor2 foo2 | gooooossshhh}"
    val result =  quoteSafeSplit(data, '|')
    assert(result.length == 3)
    assert(result(0) == "gor foo")
    assert(result(2) == " goo {gor2 foo2 | gooooossshhh}")
  }

  test("Quote safe index of sub string") {
    val data = "gor foo"
    val result =  quoteSafeIndexOf(data, "foo")
    assert(result == 4)
  }

  test("Quote safe index of sub string within quotes") {
    val data = "gor 'foo'"
    val result =  quoteSafeIndexOf(data, "foo")
    assert(result == -1)
  }

  test("Quote safe index of sub string within a block") {
    val data = "gor (foo)"
    val result =  quoteSafeIndexOf(data, "foo")
    assert(result == 5)
  }

  test("Quote safe index of sub string within a nested query") {
    val data = "gor <(gor bla)"
    val result =  quoteSafeIndexOf(data, "<(")
    assert(result == 4)
  }

  test("Quote safe index of sub string within a nested query within quotes") {
    val data = "gor '<(gor bla)'"
    val result =  quoteSafeIndexOf(data, "<(")
    assert(result == -1)
  }

  test("Testing aligned split and quotes") {
    val data = "gor (if(if(foo)) )"
    val result =  quoteSafeSplit(data, ')')
    assert(result.length == 1)
    assert(result(0) == "gor (if(if(foo)) )")
  }

  test("Testing aligned split and quotes on large query") {
    val data = "/* QC demo */\n\n def ##ref## = /mnt/csa/env/test/projects/clinical_examples/ref;\n def ##source## = /mnt/csa/env/test/projects/clinical_examples/source;\n def #wesVars# = ##source##/var/wes_varcalls.gord -s PN;\n def ##genes## = ##ref##/genes.gorz;\n def ##exons## = ##ref##/ensgenes/ensgenes_codingexons.gorz;\n\n\n def ##dbsnp## = ##ref##/dbsnp/dbsnp.gorz;\n def ##freqmax## = ##ref##/freq_max.gorz;\n create #theFreqMax# = pgor ##freqmax## | select 1-4,max_af;\n\n def ##VEP## = gor  ##source##/anno/vep_v3-4-2/vep_single_wes.gord | select 1-4,max_impact,max_consequence | varjoin -r -l -e 0 <(gor [#theFreqMax#]);\n\n\n create #chrgenecoverageindex# = pgor ##source##/cov/gene_cov_coding_seg.gord -s PN -f 'BVL_INDEX' | calc candidate 0 | calc exlt5 exomesize*lt5 | calc exlt10 exomesize*lt10 | calc exlt15 exomesize*lt15 | calc exlt20 exomesize*lt20 | calc exlt25 exomesize*lt25 | calc exlt30 exomesize*lt30 | calc attribute '95% gene depth>20,90% gene depth>20,85% gene depth>20,95% gene depth>10,90% gene depth>10,85% gene depth>10'+',exlt5,exlt10,exlt15,exlt20,exlt25,exlt30' | calc values ''+IF(lt20<0.05,1,0)+','+IF(lt20<0.1,1,0)+','+IF(lt20<0.15,1,0)+','+IF(lt10<0.05,1,0)+','+IF(lt10<0.1,1,0)+','+IF(lt10<0.15,1,0) +','+exlt5+','+exlt10+','+exlt15+','+exlt20+','+exlt25+','+exlt30 | calc bases avg_depth*exomesize | split attribute,values | group chrom -gc PN,attribute,candidate -fc values,bases,exomesize -sum -count;\n\n create #chrgenecoveragesubjects# = pgor ##source##/cov/gene_cov_coding_seg.gord -s PN -f 'CharcotMT_Father','CharcotMT_Mother','DENOVO_FATHER','DENOVO_MOTHER','OVAR2','OVAR4','OVAR6','OVAR8','OVAR11','OVAR13','AHCFATHER','ABCDEFGH','AHCMOTHER','AHCBROTHER','BVL_FATHER','BVL_MOTHER','CAMYO_CASE','OVAR15','ATMCARR2','ATMCARR3','ATMCARR4','ATMCARR1','BRCA2_INDEX','BRCA2_SISTER','ATMCARR6','BVL_INDEX','BVL_SISTER','CAMYO_INDEX','OVAR12','OVAR14','OVAR16','OVAR3','OVAR5','OVAR7','OVAR9','DENOVO_INDEX','OVAR10' | calc candidate 0 | calc exlt5 exomesize*lt5 | calc exlt10 exomesize*lt10 | calc exlt15 exomesize*lt15 | calc exlt20 exomesize*lt20 | calc exlt25 exomesize*lt25 | calc exlt30 exomesize*lt30 | calc attribute '95% gene depth>20,90% gene depth>20,85% gene depth>20,95% gene depth>10,90% gene depth>10,85% gene depth>10'+',exlt5,exlt10,exlt15,exlt20,exlt25,exlt30' | calc values ''+IF(lt20<0.05,1,0)+','+IF(lt20<0.1,1,0)+','+IF(lt20<0.15,1,0)+','+IF(lt10<0.05,1,0)+','+IF(lt10<0.1,1,0)+','+IF(lt10<0.15,1,0) +','+exlt5+','+exlt10+','+exlt15+','+exlt20+','+exlt25+','+exlt30 | calc bases avg_depth*exomesize | split attribute,values | group chrom -gc PN,attribute,candidate -fc values,bases,exomesize -sum -count;\n\n create #allgenecoverage# = gor [#chrgenecoverageindex#] [#chrgenecoveragesubjects#] | group genome -gc PN,attribute -sum -fc allCount,sum* | calc avg_depth sum_sum_bases / sum_sum_exomesize | rename sum_allCount numberOfGenes | rename sum_sum_exomeSize exomeSize | calc proportion IF(attribute ~ 'ex*',sum_sum_values,float(sum_sum_values) / numberOfGenes) | select 1-3,PN,attribute,proportion,numberOfGenes,exomeSize,avg* | calc analysis IF(attribute ~ 'ex*','exomeCoverage','geneCoverage');\n\n create #allvarchromstatbaseindex# = pgor #wesVars# -f 'BVL_INDEX' | calc varType IF(len(reference)=1 and len(call)=1,'SNPs','InDel') | join -varseg -r -l -e 0 <(gor   ##exons## | select 1-3 | segspan | calc exonic '1' ) | varjoin -r -l -e 'other' <(##VEP## | where isfloat(max_Af) | calc freqRange IF(max_Af<0.001,'veryrare (< 0.1%)',IF(max_Af<=0.01,'rare (0.1% - 1%)',IF(max_Af<=0.05,'medium (1% - 5%)','common (> 5%)')))| select 1-4,Max_Consequence,Max_Impact,freqRange) | varjoin -r -l -e 'absent' <(##dbsnp## | select 1-4 | calc indbSNP 'present') | calc transition IF(len(reference)=1 and len(call)=1 and reference+'>'+call in ('A>G','G>A','C>T','T>C'),1,0) | calc transversion IF(len(reference)=1 and len(call)=1 and transition=0,1,0) | calc zygosity IF(callCopies=1,'het','hom') | group chrom -gc PN,CallCopies,Filter,varType,exonic,Max_Impact,freqRange,zygosity,indbSNP -count -ic transition,transversion -sum | rename sum_(.*) #{1};\n\n create #allvarchromstatbasesubjects# = pgor #wesVars# -f 'CharcotMT_Father','CharcotMT_Mother','DENOVO_FATHER','DENOVO_MOTHER','OVAR2','OVAR4','OVAR6','OVAR8','OVAR11','OVAR13','AHCFATHER','ABCDEFGH','AHCMOTHER','AHCBROTHER','BVL_FATHER','BVL_MOTHER','CAMYO_CASE','OVAR15','ATMCARR2','ATMCARR3','ATMCARR4','ATMCARR1','BRCA2_INDEX','BRCA2_SISTER','ATMCARR6','BVL_INDEX','BVL_SISTER','CAMYO_INDEX','OVAR12','OVAR14','OVAR16','OVAR3','OVAR5','OVAR7','OVAR9','DENOVO_INDEX','OVAR10'| calc varType IF(len(reference)=1 and len(call)=1,'SNPs','InDel') | join -varseg -r -l -e 0 <(gor  ##exons## | select 1-3 | segspan | calc exonic '1' ) | varjoin -r -l -e 'other' <(##VEP## | where isfloat(max_Af) | calc freqRange IF(max_Af<0.001,'veryrare (< 0.1%)',IF(max_Af<=0.01,'rare (0.1% - 1%)',IF(max_Af<=0.05,'medium (1% - 5%)','common (> 5%)')))| select 1-4,Max_Consequence,Max_Impact,freqRange) | varjoin -r -l -e 'absent' <(##dbsnp## | select 1-4 | calc indbSNP 'present') | calc transition IF(len(reference)=1 and len(call)=1 and reference+'>'+call in ('A>G','G>A','C>T','T>C'),1,0) | calc transversion IF(len(reference)=1 and len(call)=1 and transition=0,1,0) | calc zygosity IF(callCopies=1,'het','hom') | group chrom -gc PN,CallCopies,Filter,varType,exonic,Max_Impact,freqRange,zygosity,indbSNP -count -ic transition,transversion -sum | rename sum_(.*) #{1};\n\n create #allvarstatbase# = gor [#allvarchromstatbaseindex#] [#allvarchromstatbasesubjects#] | where not (chrom IN ('chrX','chrY','chrM','chrXY')) | where exonic = 1 | group genome -gc PN,CallCopies,Filter,varType,exonic,Max_Impact,freqRange,zygosity,indbSNP -sum -ic allCount- | rename sum_(.*) #{1};\n\n def allcalcproportion($1,$2) = [#allvarstatbase#] | merge <(gor [#allvarstatbase#] | group genome -gc PN | join -segseg -r <([#allvarstatbase#] | group genome -gc $1) | calc allCount '0' ) | group genome -gc PN,$1 -sum -ic allCount | granno genome -gc PN -sum -ic sum_allCount | calc attribute $1 | calc proportion 100*float(sum_allCount)/sum_sum_allCount | rename sum_sum_allcount totalVars | hide sum* | calc analysis $2;\n\n create #allanalysis# = gor [#allgenecoverage#] | merge <( gor [#allvarstatbase#] | group genome -gc PN -sum -ic transition,transversion | calc proportion float(sum_transition)/sum_transversion | calc totalVars form(sum_transition+sum_transversion,15,12) | hide sum* | calc analysis 'transition transversion Analysis' | calc attribute 'tt_ratio' ) | merge <( allcalcproportion(indbsnp,'dbSNP Analysis') ) | merge <( allcalcproportion(zygosity,'zygosity Analysis') ) | merge <( allcalcproportion(freqRange,'frequency Analysis') ) | merge <( allcalcproportion(max_impact,'impact Analysis' ) ) | merge <( allcalcproportion(Filter,'quality Analysis' ) ) | merge <( allcalcproportion(varType,'SNP vs InDel Analysis' ) )\n | select 1-3,PN,analysis,attribute,proportion,totalvars,avg_depth,numberOfGenes,exomeSize\n | granno genome -gc analysis,attribute -fc proportion,avg_depth -avg -std -count\n | rank genome proportion -gc analysis,attribute -o desc -z -q | rename rank_proportion rank_perc_FromTop | rename lowOReqRank lowOReqRankFromTop | hide eqRank\n | rank genome proportion -gc analysis,attribute -o asc -q | rename rank_proportion rank_perc_FromBottom | rename lowOReqRank lowOReqRankFromBottom | hide eqRank | rename allCount PNcount | calc ratio IF(attribute = 'tt_ratio',proportion,(proportion/100.0)/(1.0-proportion/100.0)) | calc InDistribution IF(z_proportion < -2.0 or lowOReqRankFromBottom <= 0.05,'Low',IF(z_proportion > 2.0 or lowOReqRankFromTop <= 0.05,'High','Norm')) | calc Color IF(z_proportion < -3.0 or z_proportion > 3.0 ,'Red',IF(z_proportion < -2.0 or z_proportion > 2.0,'Orange','Green')) ;\n\n gor [#allanalysis#] | replace proportion IF(attribute ~ 'exlt*',FORM(proportion/exomeSize,1,5),FORM(proportion,4,2)) | replace numberOfgenes int(numberOfGenes) | replace avg_proportion form(avg_proportion,4,2) | replace std_proportion form(std_proportion,4,2) | replace ratio form(ratio,4,2) | replace exomeSize round(exomeSize) | prefix 3- all | rename all_PN PN | rename all_analysis analysis | rename all_attribute attribute | distinct | sort genome"
    val result =  quoteSafeSplit(data, '|')
    assert(result.length == 98)
  }

  test("Testing quote safe index of large query") {
    val data = "/* QC demo */\n\n def ##ref## = /mnt/csa/env/test/projects/clinical_examples/ref;\n def ##source## = /mnt/csa/env/test/projects/clinical_examples/source;\n def #wesVars# = ##source##/var/wes_varcalls.gord -s PN;\n def ##genes## = ##ref##/genes.gorz;\n def ##exons## = ##ref##/ensgenes/ensgenes_codingexons.gorz;\n\n\n def ##dbsnp## = ##ref##/dbsnp/dbsnp.gorz;\n def ##freqmax## = ##ref##/freq_max.gorz;\n create #theFreqMax# = pgor ##freqmax## | select 1-4,max_af;\n\n def ##VEP## = gor  ##source##/anno/vep_v3-4-2/vep_single_wes.gord | select 1-4,max_impact,max_consequence | varjoin -r -l -e 0 <(gor [#theFreqMax#]);\n\n\n create #chrgenecoverageindex# = pgor ##source##/cov/gene_cov_coding_seg.gord -s PN -f 'BVL_INDEX' | calc candidate 0 | calc exlt5 exomesize*lt5 | calc exlt10 exomesize*lt10 | calc exlt15 exomesize*lt15 | calc exlt20 exomesize*lt20 | calc exlt25 exomesize*lt25 | calc exlt30 exomesize*lt30 | calc attribute '95% gene depth>20,90% gene depth>20,85% gene depth>20,95% gene depth>10,90% gene depth>10,85% gene depth>10'+',exlt5,exlt10,exlt15,exlt20,exlt25,exlt30' | calc values ''+IF(lt20<0.05,1,0)+','+IF(lt20<0.1,1,0)+','+IF(lt20<0.15,1,0)+','+IF(lt10<0.05,1,0)+','+IF(lt10<0.1,1,0)+','+IF(lt10<0.15,1,0) +','+exlt5+','+exlt10+','+exlt15+','+exlt20+','+exlt25+','+exlt30 | calc bases avg_depth*exomesize | split attribute,values | group chrom -gc PN,attribute,candidate -fc values,bases,exomesize -sum -count;\n\n create #chrgenecoveragesubjects# = pgor ##source##/cov/gene_cov_coding_seg.gord -s PN -f 'CharcotMT_Father','CharcotMT_Mother','DENOVO_FATHER','DENOVO_MOTHER','OVAR2','OVAR4','OVAR6','OVAR8','OVAR11','OVAR13','AHCFATHER','ABCDEFGH','AHCMOTHER','AHCBROTHER','BVL_FATHER','BVL_MOTHER','CAMYO_CASE','OVAR15','ATMCARR2','ATMCARR3','ATMCARR4','ATMCARR1','BRCA2_INDEX','BRCA2_SISTER','ATMCARR6','BVL_INDEX','BVL_SISTER','CAMYO_INDEX','OVAR12','OVAR14','OVAR16','OVAR3','OVAR5','OVAR7','OVAR9','DENOVO_INDEX','OVAR10' | calc candidate 0 | calc exlt5 exomesize*lt5 | calc exlt10 exomesize*lt10 | calc exlt15 exomesize*lt15 | calc exlt20 exomesize*lt20 | calc exlt25 exomesize*lt25 | calc exlt30 exomesize*lt30 | calc attribute '95% gene depth>20,90% gene depth>20,85% gene depth>20,95% gene depth>10,90% gene depth>10,85% gene depth>10'+',exlt5,exlt10,exlt15,exlt20,exlt25,exlt30' | calc values ''+IF(lt20<0.05,1,0)+','+IF(lt20<0.1,1,0)+','+IF(lt20<0.15,1,0)+','+IF(lt10<0.05,1,0)+','+IF(lt10<0.1,1,0)+','+IF(lt10<0.15,1,0) +','+exlt5+','+exlt10+','+exlt15+','+exlt20+','+exlt25+','+exlt30 | calc bases avg_depth*exomesize | split attribute,values | group chrom -gc PN,attribute,candidate -fc values,bases,exomesize -sum -count;\n\n create #allgenecoverage# = gor [#chrgenecoverageindex#] [#chrgenecoveragesubjects#] | group genome -gc PN,attribute -sum -fc allCount,sum* | calc avg_depth sum_sum_bases / sum_sum_exomesize | rename sum_allCount numberOfGenes | rename sum_sum_exomeSize exomeSize | calc proportion IF(attribute ~ 'ex*',sum_sum_values,float(sum_sum_values) / numberOfGenes) | select 1-3,PN,attribute,proportion,numberOfGenes,exomeSize,avg* | calc analysis IF(attribute ~ 'ex*','exomeCoverage','geneCoverage');\n\n create #allvarchromstatbaseindex# = pgor #wesVars# -f 'BVL_INDEX' | calc varType IF(len(reference)=1 and len(call)=1,'SNPs','InDel') | join -varseg -r -l -e 0 <(gor   ##exons## | select 1-3 | segspan | calc exonic '1' ) | varjoin -r -l -e 'other' <(##VEP## | where isfloat(max_Af) | calc freqRange IF(max_Af<0.001,'veryrare (< 0.1%)',IF(max_Af<=0.01,'rare (0.1% - 1%)',IF(max_Af<=0.05,'medium (1% - 5%)','common (> 5%)')))| select 1-4,Max_Consequence,Max_Impact,freqRange) | varjoin -r -l -e 'absent' <(##dbsnp## | select 1-4 | calc indbSNP 'present') | calc transition IF(len(reference)=1 and len(call)=1 and reference+'>'+call in ('A>G','G>A','C>T','T>C'),1,0) | calc transversion IF(len(reference)=1 and len(call)=1 and transition=0,1,0) | calc zygosity IF(callCopies=1,'het','hom') | group chrom -gc PN,CallCopies,Filter,varType,exonic,Max_Impact,freqRange,zygosity,indbSNP -count -ic transition,transversion -sum | rename sum_(.*) #{1};\n\n create #allvarchromstatbasesubjects# = pgor #wesVars# -f 'CharcotMT_Father','CharcotMT_Mother','DENOVO_FATHER','DENOVO_MOTHER','OVAR2','OVAR4','OVAR6','OVAR8','OVAR11','OVAR13','AHCFATHER','ABCDEFGH','AHCMOTHER','AHCBROTHER','BVL_FATHER','BVL_MOTHER','CAMYO_CASE','OVAR15','ATMCARR2','ATMCARR3','ATMCARR4','ATMCARR1','BRCA2_INDEX','BRCA2_SISTER','ATMCARR6','BVL_INDEX','BVL_SISTER','CAMYO_INDEX','OVAR12','OVAR14','OVAR16','OVAR3','OVAR5','OVAR7','OVAR9','DENOVO_INDEX','OVAR10'| calc varType IF(len(reference)=1 and len(call)=1,'SNPs','InDel') | join -varseg -r -l -e 0 <(gor  ##exons## | select 1-3 | segspan | calc exonic '1' ) | varjoin -r -l -e 'other' <(##VEP## | where isfloat(max_Af) | calc freqRange IF(max_Af<0.001,'veryrare (< 0.1%)',IF(max_Af<=0.01,'rare (0.1% - 1%)',IF(max_Af<=0.05,'medium (1% - 5%)','common (> 5%)')))| select 1-4,Max_Consequence,Max_Impact,freqRange) | varjoin -r -l -e 'absent' <(##dbsnp## | select 1-4 | calc indbSNP 'present') | calc transition IF(len(reference)=1 and len(call)=1 and reference+'>'+call in ('A>G','G>A','C>T','T>C'),1,0) | calc transversion IF(len(reference)=1 and len(call)=1 and transition=0,1,0) | calc zygosity IF(callCopies=1,'het','hom') | group chrom -gc PN,CallCopies,Filter,varType,exonic,Max_Impact,freqRange,zygosity,indbSNP -count -ic transition,transversion -sum | rename sum_(.*) #{1};\n\n create #allvarstatbase# = gor [#allvarchromstatbaseindex#] [#allvarchromstatbasesubjects#] | where not (chrom IN ('chrX','chrY','chrM','chrXY')) | where exonic = 1 | group genome -gc PN,CallCopies,Filter,varType,exonic,Max_Impact,freqRange,zygosity,indbSNP -sum -ic allCount- | rename sum_(.*) #{1};\n\n def allcalcproportion($1,$2) = [#allvarstatbase#] | merge <(gor [#allvarstatbase#] | group genome -gc PN | join -segseg -r <([#allvarstatbase#] | group genome -gc $1) | calc allCount '0' ) | group genome -gc PN,$1 -sum -ic allCount | granno genome -gc PN -sum -ic sum_allCount | calc attribute $1 | calc proportion 100*float(sum_allCount)/sum_sum_allCount | rename sum_sum_allcount totalVars | hide sum* | calc analysis $2;\n\n create #allanalysis# = gor [#allgenecoverage#] | merge <( gor [#allvarstatbase#] | group genome -gc PN -sum -ic transition,transversion | calc proportion float(sum_transition)/sum_transversion | calc totalVars form(sum_transition+sum_transversion,15,12) | hide sum* | calc analysis 'transition transversion Analysis' | calc attribute 'tt_ratio' ) | merge <( allcalcproportion(indbsnp,'dbSNP Analysis') ) | merge <( allcalcproportion(zygosity,'zygosity Analysis') ) | merge <( allcalcproportion(freqRange,'frequency Analysis') ) | merge <( allcalcproportion(max_impact,'impact Analysis' ) ) | merge <( allcalcproportion(Filter,'quality Analysis' ) ) | merge <( allcalcproportion(varType,'SNP vs InDel Analysis' ) )\n | select 1-3,PN,analysis,attribute,proportion,totalvars,avg_depth,numberOfGenes,exomeSize\n | granno genome -gc analysis,attribute -fc proportion,avg_depth -avg -std -count\n | rank genome proportion -gc analysis,attribute -o desc -z -q | rename rank_proportion rank_perc_FromTop | rename lowOReqRank lowOReqRankFromTop | hide eqRank\n | rank genome proportion -gc analysis,attribute -o asc -q | rename rank_proportion rank_perc_FromBottom | rename lowOReqRank lowOReqRankFromBottom | hide eqRank | rename allCount PNcount | calc ratio IF(attribute = 'tt_ratio',proportion,(proportion/100.0)/(1.0-proportion/100.0)) | calc InDistribution IF(z_proportion < -2.0 or lowOReqRankFromBottom <= 0.05,'Low',IF(z_proportion > 2.0 or lowOReqRankFromTop <= 0.05,'High','Norm')) | calc Color IF(z_proportion < -3.0 or z_proportion > 3.0 ,'Red',IF(z_proportion < -2.0 or z_proportion > 2.0,'Orange','Green')) ;\n\n gor [#allanalysis#] | replace proportion IF(attribute ~ 'exlt*',FORM(proportion/exomeSize,1,5),FORM(proportion,4,2)) | replace numberOfgenes int(numberOfGenes) | replace avg_proportion form(avg_proportion,4,2) | replace std_proportion form(std_proportion,4,2) | replace ratio form(ratio,4,2) | replace exomeSize round(exomeSize) | prefix 3- all | rename all_PN PN | rename all_analysis analysis | rename all_attribute attribute | distinct | sort genome"
    val result =  quoteSafeIndexOf(data, "max_Af", par = false, 0)
    assert(result == 3409)
  }

  test("Calc expression with quoted parenthesis") {
    val data = "gor test2321.gor | calc new substr(replace(old,')',''),0,5)"
    val result = quoteSafeSplit(data, '|')
    assert(result.length == 2)
  }
}