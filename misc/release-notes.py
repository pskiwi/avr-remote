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


def to_markdown(item):
	# The bold in "<b>This version needs Android 7.0 or newer.</b>" is the point of that entry,
	# so carry b/i over instead of dropping them with the rest of the tags. Unescape only after
	# the tags are gone, or an escaped &lt;b&gt; would turn into one.
	text = re.sub(r"</?(?:b|strong)>", "**", item)
	text = re.sub(r"</?(?:i|em)>", "*", text)
	text = re.sub(r"<[^>]+>", "", text)
	return " ".join(html.unescape(text).split())


def extract(source, version):
	block = re.search(r"<b>\s*" + re.escape(version) + r"\s*</b>\s*<ul>(.*?)</ul>", source, re.S)
	if block is None:
		return None
	return [to_markdown(i) for i in re.findall(r"<li>(.*?)</li>", block.group(1), re.S)]


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
