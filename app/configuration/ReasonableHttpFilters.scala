package configuration

import play.api.mvc.EssentialFilter
import play.filters.csrf.CSRFComponents
import play.filters.headers.SecurityHeadersComponents

/*
This is based off the original Play class HttpFiltersComponents,
with allowedHostsFilter removed so we can use with randomly-named
autoscaled EC2 boxes, or whatever Heroku does.
 */
trait ReasonableHttpFilters extends CSRFComponents with SecurityHeadersComponents {

  def httpFilters: Seq[EssentialFilter] = Seq(csrfFilter, securityHeadersFilter)
}