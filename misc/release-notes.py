#!/usr/bin/env python3
"""Turn one version's block of whatsnew.html into Markdown for the GitHub release body.

Usage: python3 misc/release-notes.py <tag>

<tag> is the git tag, e.g. v1.6.1. The leading "v" is stripped and the rest has to match a
<b>version</b> heading in app/src/main/assets/whatsnew.html exactly -- that file is written
anyway (RELEASE.md, step 2), so the release page and the in-app dialog cannot drift apart.

The notes go to stdout. No matching block -- an rc tag, or notes not written yet -- prints
nothing and exits 1; the release workflow reads that as "fall back to GitHub's generated
notes", not as a failure. Never let this abort a release: the tag is already pushed and the
APK already built by the time it runs.
"""

import html
import pathlib
import re
import sys

WHATSNEW = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/whatsnew.html"


# Markdown means something by characters that are ordinary prose in the source. The receiver
# families are written "AVR-*13" and "Marantz-SR*7", so an entry naming two of them would come
# out italicised with the asterisks eaten.
MARKDOWN_SPECIAL = re.compile(r"([\\`*_\[\]<])")


def to_markdown(item):
	# Walk tags and text separately: the text has to be escaped, the markers this adds must not
	# be. The bold in "<b>This version needs Android 7.0 or newer.</b>" is the point of that
	# entry, so carry b/i over instead of dropping them with the rest of the tags -- links lose
	# their href, which is why the entries have to read as prose rather than as "see here".
	out = []
	for part in re.split(r"(<[^>]+>)", item):
		if not part.startswith("<"):
			out.append(MARKDOWN_SPECIAL.sub(r"\\\1", html.unescape(part)))
		elif re.fullmatch(r"</?(?:b|strong)>", part, re.I):
			out.append("**")
		elif re.fullmatch(r"</?(?:i|em)>", part, re.I):
			out.append("*")
	return " ".join("".join(out).split())


def extract(source, version):
	block = re.search(r"<b>\s*" + re.escape(version) + r"\s*</b>\s*<ul>(.*?)</ul>", source, re.S)
	if block is None:
		return None
	# Hand-written HTML: one entry is spelled <lI>, another never closes its <li>. Splitting on
	# the opening tag survives both, where a <li>(.*?)</li> pair drops the first silently and
	# merges the second into its neighbour.
	items = re.split(r"<li>", block.group(1), flags=re.I)[1:]
	return [to_markdown(re.split(r"</li>", i, flags=re.I)[0]) for i in items]


def main(argv):
	if len(argv) != 2:
		sys.exit(__doc__)
	version = argv[1].removeprefix("v")
	items = extract(WHATSNEW.read_text(encoding="utf-8"), version)
	if not items:
		print("no <b>%s</b> block in %s" % (version, WHATSNEW.name), file=sys.stderr)
		return 1
	print("\n".join("- " + i for i in items))
	return 0


if __name__ == "__main__":
	sys.exit(main(sys.argv))
