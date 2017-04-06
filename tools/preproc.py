#!/usr/bin/env python3

import os
import sys
import tempfile

import argparse
import pygments
import pygments.lexers as lexers
from pygments.token import Token

from typing import Any, Dict, Tuple

def parse():
    mappings = {}  # type: Dict[str, str]
    args = sys.argv
    i = 1
    while i < len(args):
        cand = args[i]
        if '--' == cand:
            i += 1
            break
        if cand.startswith('-D'):
            parts = cand[2:].split('=', 1)
            if not parts[0]:
                print('illegal empty mapping:', cand)
                sys.exit(2)
            mappings[parts[0]] = parts[1] if len(parts) != 1 else None
        elif cand.startswith('-'):
            print('illegal argument:', cand)
            sys.exit(2)
        else:
            break

        i += 1

    files = args[i:]

    if not files:
        print('no files provided')
        sys.exit(2)

    return mappings, files

def is_name(tup: Tuple[Any, str]) -> bool:
    t, val = tup
    return t is Token.Name or t is Token.Keyword

def is_junk(tup: Tuple[Any, str]) -> bool:
    t, val = tup
    return (t is Token.Text and '\n' not in val) or (
            t is Token.Comment and not t is Token.Comment.Preproc)

def apply(mappings: Dict[str, str], content: str) -> str:
    it = iter(pygments.lex(content, lexers.CppLexer()))
    out = ''
    prev = next(it)
    out += prev[1]
    now = next(it)
    while now:
        val = now[1]
        if is_junk(now):
            out += val
        elif is_name(now):
            if val in mappings:
                repl = mappings[val]
                if repl:
                    out += repl
            else:
                out += val
        elif now[0] is Token.Text and '\n' in val:
            out += val
        else:
            raise Exception('invalid token "{}" after {}'.format(now, out))

        prev = now
        now = next(it, None)
    return out

def main():
    mappings, files = parse()

    for file in files:
        with open(file) as f:
            orig = f.read()
        new = apply(mappings, orig)
        if new == orig:
            continue
        fd, name = tempfile.mkstemp(dir=os.path.dirname(file), prefix='.preproc.', suffix='.tmp')
        with os.fdopen(fd, 'w') as f:
            f.write(new)
        os.rename(name, file)



if '__main__' == __name__:
    main()

# list(pygments.lex('int main(){\n# ifdef lol\nq();\n#endif\nfoo(bar()); }', CppLexer()))

