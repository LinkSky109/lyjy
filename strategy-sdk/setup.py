from setuptools import setup, find_packages

setup(
    name="lhjypt",
    version="0.1.0",
    description="企业级量化交易平台策略SDK",
    author="lhjypt",
    packages=find_packages(),
    python_requires=">=3.9",
    install_requires=[],
    extras_require={
        "dev": ["pytest>=7.0", "pytest-cov"],
    },
)
