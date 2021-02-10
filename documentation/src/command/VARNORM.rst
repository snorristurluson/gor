.. raw:: html

   <span style="float:right; padding-top:10px; color:#BABABA;">Used in: gor only</span>

.. _VARNORM:

=======
VARNORM
=======
The :ref:`VARNORM` command normalises the variation data in a gor stream to the left or right. The column containing the reference and alternate allele must be specified. If neither ``-left`` nor ``-right`` is specified, then left normalisation is default.

If the ``-trim`` command is used, then any redundant bases in the representations of insertions or deletions will be deleted (i.e. deletions will be represented by empty cells in Call - or in Reference in the case of insertions).

By default, normalisation is run automatically as part of a VARJOIN command, however, if variants need to be grouped (e.g. for counting or generating horizontal data)
the InDels must be normalized.  For very large InDels a high span may be needed, however, we don't recommend excessive span.  Consider then
the use of pre-normalized data and the -norm option in VARJOIN.  The system can be configured to default on -norm behaviour for VARJOIN.

Usage
=====

.. code-block:: gor

   gor ... | VARNORM refcol altcol [ -seg | -left | -right | -trim | -span ]

Options
=======

+--------------+------------------------------------------------------------------------------------+
| ``-seg``     | The variant is denoted as segment, e.g. (chr,bpstart,bpstop,ref,call).             |
+--------------+------------------------------------------------------------------------------------+
| ``-left``    | Normalise the variation data to the left.                                          |
+--------------+------------------------------------------------------------------------------------+
| ``-right``   | Normalise the variation data to the right.                                         |
+--------------+------------------------------------------------------------------------------------+
| ``-trim``    | Trims the redundant bases away from the defined columns (ref + alt)                |
+--------------+------------------------------------------------------------------------------------+
| ``-span``    | Max merge span. The default is 100bp, max 1Mb (crazy high!)                        |
+--------------+------------------------------------------------------------------------------------+

Examples
========
.. code-block:: gor

   gor -p chr1:723798-723802 #dbsnp# | CALC oldpos pos | VARNORM -right reference allele

.. code-block:: gor

   gor #dbsnp# | VARNORM -left #3 #4 | GROUP 1 -gc #3,#4 -count | WHERE allcount > 1 | TOP 2


Related commands
----------------

:ref:`VARMERGE` :ref:`VARJOIN`