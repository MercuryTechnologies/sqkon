#!/usr/bin/env ruby
# Post-build smoke check for the Jekyll output. Catches structural regressions
# that valid-HTML linters miss — most importantly, the nested-anchor bug where
# Just the Docs injects <a class="anchor-heading"> inside an <h3> sitting
# inside our outer <a class="feature-card">. Browsers parse this per HTML5
# rules and auto-close the outer link, leaving the cards visually empty.
#
# IMPORTANT: this uses Nokogiri::HTML5, not Nokogiri::HTML. The default HTML4
# parser is lenient about nested anchors and would NOT see the bug; HTML5
# parsing matches what real browsers do, so the assertion below is meaningful.
require "nokogiri"

abort "validate-docs.rb requires Nokogiri::HTML5 (nokogiri >= 1.12)" unless defined?(Nokogiri::HTML5)

site = ARGV[0] || "_site"
index = File.join(site, "index.html")
abort "missing #{index}" unless File.exist?(index)

doc = Nokogiri::HTML5(File.read(index))
errors = []

cards = doc.css("a.feature-card")
errors << "expected 6 .feature-card anchors, found #{cards.size}" unless cards.size == 6

cards.each_with_index do |a, i|
  errors << "feature-card[#{i}] missing href" if a["href"].to_s.empty?
  text = a.text.strip
  errors << "feature-card[#{i}] is empty after HTML5 parsing — likely nested-anchor or kramdown auto-close" if text.empty?
  title = a.at_css(".feature-card__title")
  errors << "feature-card[#{i}] missing .feature-card__title" if title.nil? || title.text.strip.empty?
end

logo = doc.at_css("img.site-logo")
errors << "missing .site-logo img in header" unless logo
errors << ".site-logo src does not point at logo.png (got #{logo["src"].inspect})" if logo && !logo["src"].to_s.end_with?("logo.png")

# --- Visual refresh: self-hosted fonts ---
preloads = doc.css('link[rel="preload"][as="font"]')
errors << "expected 2 font preloads, found #{preloads.size}" unless preloads.size == 2
%w[inter-var-latin.woff2 ibm-plex-mono-400-latin.woff2].each do |f|
  path = File.join(site, "assets", "fonts", f)
  errors << "missing font file #{f}" unless File.exist?(path)
end
font_bytes = Dir[File.join(site, "assets", "fonts", "*.woff2")].sum { |f| File.size(f) }
errors << "font payload #{font_bytes / 1024}KB exceeds 180KB budget" if font_bytes > 180 * 1024

# --- Visual refresh: theme toggle ---
errors << "missing #sqkon-theme-toggle button" unless doc.at_css("button#sqkon-theme-toggle")
errors << "missing sqkon-docs.js script tag" unless doc.at_css('script[src*="sqkon-docs.js"]')

if errors.empty?
  puts "docs smoke check OK"
else
  warn "docs smoke check FAILED:"
  errors.each { |e| warn "  - #{e}" }
  exit 1
end
