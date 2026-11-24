import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import bootstrap_backend as bootstrap


class DevelopmentRoutingTest(unittest.TestCase):
    def test_only_inspected_site_and_single_analysis_snippet_are_allowed(self):
        legacy='proxy_pass http://127.0.0.1:8080;'
        with tempfile.TemporaryDirectory() as folder:
            snippet=Path(folder)/'analysis.conf'
            snippet.write_text(legacy)
            site=legacy+'\n'+legacy+'\ninclude '+str(snippet)+';\n'
            with patch.object(bootstrap.deployment,'ENVIRONMENT','development'), patch.object(bootstrap,'DEVELOPMENT_API_SNIPPET',snippet), patch.object(bootstrap.deployment,'command',return_value=legacy*3) as nginx:
                bootstrap.validate_routing_layout(site)
                nginx.assert_called_once_with('nginx','-T')
                with self.assertRaisesRegex(RuntimeError,'outside'):
                    nginx.return_value=legacy*4
                    bootstrap.validate_routing_layout(site)
                for unreviewed in (site.replace('include ','#include '), site+legacy,site.replace(str(snippet),'/other.conf')):
                    with self.assertRaisesRegex(RuntimeError,'differs'):
                        bootstrap.validate_routing_layout(unreviewed)

    def test_production_still_requires_original_four_routes(self):
        legacy='proxy_pass http://127.0.0.1:8080;'
        with patch.object(bootstrap.deployment,'ENVIRONMENT','production'):
            bootstrap.validate_routing_layout(legacy*4)
            with self.assertRaisesRegex(RuntimeError,'four API routes'):
                bootstrap.validate_routing_layout(legacy*3)


if __name__=='__main__':unittest.main()
