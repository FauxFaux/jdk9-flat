import preproc

def run(msg: str) -> str:
    return preproc.apply({
        'foo': 'bar',
        }, msg)

def test_foo():
    assert run('yeah you foo you go') == 'yeah you bar you go\n'

